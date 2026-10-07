package cn.blockforge.meteorzhcn.font_fix;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.Texture;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackRange;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按需打包字形的小字体图集。
 *
 * <p>Meteor 自带的字体渲染器在初始化时只把固定的几段码位（ASCII、拉丁扩展、西里尔等）
 * 打进图集，汉字、假名这类码位根本不在表里，于是中文会整体显示成空白。这里保留打包上下文，
 * 遇到图集里没有的码位就即时补打进去，让任意字体里有的字形都能画出来。</p>
 *
 * <p>这个图集直接写在 {@code Font} 自己那张 2048×2048 纹理上（尺寸一致），因此不新建纹理、
 * 也不碰 Minecraft 的纹理格式枚举——26.1 用 {@code TextureFormat.RED8}，26.2 换成了
 * {@code GpuFormat.R8_UNORM}，那正是跨版本最容易崩的一处。纹理的关闭仍归
 * {@code CustomTextRenderer.destroy()} 管，这里只负责往里面写字形。</p>
 *
 * <p>性能上做了三件事：纹理只复用不新建、补字形时不再整张重传（改传脏矩形）；
 * 已经确认字形齐全的字符串直接命中缓存，不再逐字查表；量过的宽度缓存下来。</p>
 */
public class FontFix {
    public static final Logger LOG = LoggerFactory.getLogger("Meteor-I18n-XC/font");

    public final Texture texture;
    private final int height;
    private final float scale;
    private final float ascent;
    private final Int2ObjectOpenHashMap<CharData> charMap = new Int2ObjectOpenHashMap<>();
    private static final int size = 2048;
    /** 脏矩形超过这个像素数就退回整张上传：复制中转缓冲的代价已经接近全量重传了。 */
    private static final int MAX_REGION_PIXELS = 1 << 20;
    /** 字符串缓存容量上限，超出整体清空，避免 HUD 上的动态文本把内存越堆越大。 */
    private static final int CACHE_LIMIT = 4096;
    /** 超过这个长度的字符串不进缓存：长文本基本不复用，缓存它只会挤掉有用的短标签。 */
    private static final int CACHE_MAX_LENGTH = 256;
    /** 每批最多打包多少个字形：码位加 chardata 要放进默认 64 KB 的栈帧里。 */
    private static final int PACK_CHUNK_SIZE = 1024;

    /**
     * 26.2 的 CommandEncoder 去掉了 {@code NativeImage.Format} 参数（8 参），26.1 还带着（9 参）；
     * 两者剩下的 6 个 int 都是 (mipLevel, depth, x, y, width, height)，所以同一套坐标即可。
     * 运行时挑一个，找不到就退回整张上传，字形仍然能显示。
     */
    private static Method regionUpload;
    private static boolean regionUploadResolved;

    private final ByteBuffer buffer;
    private final STBTTFontinfo fontInfo;
    private final ByteBuffer bitmap;
    private final STBTTPackContext packContext;

    /** 已确认字形齐全的字符串，命中后不必再逐字查 charMap。 */
    private final ObjectOpenHashSet<String> loadedStrings = new ObjectOpenHashSet<>();
    /** 已量过宽度的字符串 → 宽度，Meteor 布局每帧会对同一字符串问很多次。 */
    private final Object2DoubleOpenHashMap<String> widthCache = new Object2DoubleOpenHashMap<>();
    /** 上传脏矩形时复用的中转缓冲，避免每次补字都新建一块原生内存。 */
    private ByteBuffer uploadBuffer;

    /** 图集是否已经关闭。字体重载失败时 Meteor 会回退再来一次，close 可能被连着调两次。 */
    private boolean closed = false;

    public FontFix(ByteBuffer buffer, int height, Texture texture) {
        this.buffer = buffer;
        this.height = height;
        this.texture = texture;
        this.fontInfo = STBTTFontinfo.create();

        // 字体文件损坏/为空时 stbtt_InitFont 会返回 0，之后拿这个 fontInfo 继续打包是未定义行为，
        // 最坏会直接把 JVM 带崩。这里提前失败，让上层放弃动态字形、退回 Meteor 原行为。
        if (!STBTruetype.stbtt_InitFont(this.fontInfo, buffer)) {
            throw new IllegalArgumentException("无法解析字体文件（stbtt_InitFont 失败）");
        }

        this.bitmap = BufferUtils.createByteBuffer(4194304);
        this.packContext = STBTTPackContext.create();
        STBTruetype.stbtt_PackBegin(this.packContext, this.bitmap, 2048, 2048, 0, 1);
        this.scale = STBTruetype.stbtt_ScaleForPixelHeight(this.fontInfo, (float) height);

        MemoryStack stack = MemoryStack.stackPush();

        try {
            IntBuffer ascent = stack.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(this.fontInfo, ascent, null, null);
            this.ascent = (float) ascent.get(0);
        } finally {
            stack.close();
        }

        this.preloadAsciiCharacters();
    }

    private void preloadAsciiCharacters() {
        MemoryStack stack = MemoryStack.stackPush();

        try {
            STBTTPackedchar.Buffer cdata = STBTTPackedchar.malloc(128, stack);
            STBTTPackRange.Buffer packRange = STBTTPackRange.malloc(1, stack);
            packRange.put(STBTTPackRange.malloc(stack).set((float) this.height, 32, null, 128, cdata, (byte) 2, (byte) 2));
            packRange.flip();
            STBTruetype.stbtt_PackFontRanges(this.packContext, this.buffer, 0, packRange);

            for (int i = 0; i < 128; i++) {
                this.putCharData(i + 32, cdata.get(i));
            }
        } finally {
            stack.close();
        }

        // 建好之后图集还是空的，这一次整张上传即可；以后补字形只传脏矩形。
        this.texture.upload(this.bitmap);
    }

    private void loadCharacter(IntArrayList codePoints) {
        if (this.closed) {
            // 图集已经关闭，再往 packContext 里塞字形是未定义行为。
            return;
        }

        IntArrayList missing = new IntArrayList(codePoints.size());
        IntOpenHashSet seen = new IntOpenHashSet(codePoints.size());

        for (int i = 0; i < codePoints.size(); i++) {
            int codePoint = codePoints.getInt(i);
            if (this.charMap.containsKey(codePoint) || !seen.add(codePoint)) {
                continue;
            }

            missing.add(codePoint);
        }

        int count = missing.size();
        if (count == 0) {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        // 一次把整批缺字交给 stb。原实现逐字调用 stbtt_PackFontRanges，
        // 一个长标签会拆成几十次原生调用；这里合并成一批，过长的再分块。
        // 码位与 chardata 都放在 MemoryStack 的栈帧里：原生调用期间它们不会被回收，
        // 栈帧关闭后内存也随之释放。
        for (int start = 0; start < count; start += PACK_CHUNK_SIZE) {
            int chunk = Math.min(PACK_CHUNK_SIZE, count - start);
            MemoryStack stack = MemoryStack.stackPush();

            try {
                IntBuffer codePointBuffer = stack.mallocInt(chunk);

                for (int i = 0; i < chunk; i++) {
                    codePointBuffer.put(missing.getInt(start + i));
                }

                codePointBuffer.flip();

                STBTTPackedchar.Buffer cdata = STBTTPackedchar.malloc(chunk, stack);
                STBTTPackRange.Buffer packRange = STBTTPackRange.malloc(1, stack);
                packRange.put(STBTTPackRange.malloc(stack).set((float) this.height, 0, codePointBuffer, chunk, cdata, (byte) 2, (byte) 2));
                packRange.flip();
                STBTruetype.stbtt_PackFontRanges(this.packContext, this.buffer, 0, packRange);

                for (int i = 0; i < chunk; i++) {
                    STBTTPackedchar packedChar = cdata.get(i);
                    this.putCharData(missing.getInt(start + i), packedChar);
                    minX = Math.min(minX, packedChar.x0());
                    minY = Math.min(minY, packedChar.y0());
                    maxX = Math.max(maxX, packedChar.x1());
                    maxY = Math.max(maxY, packedChar.y1());
                }
            } finally {
                stack.close();
            }
        }

        this.uploadRegion(minX, minY, maxX - minX, maxY - minY);
    }

    private void putCharData(int codePoint, STBTTPackedchar packedChar) {
        float ipw = 4.8828125E-4F;
        float iph = 4.8828125E-4F;
        this.charMap.put(codePoint, new CharData(
                packedChar.xoff(),
                packedChar.yoff(),
                packedChar.xoff2(),
                packedChar.yoff2(),
                (float) packedChar.x0() * ipw,
                (float) packedChar.y0() * iph,
                (float) packedChar.x1() * ipw,
                (float) packedChar.y1() * iph,
                packedChar.xadvance()
        ));
    }

    /** 只把新增字形占据的矩形写进显存。 */
    private void uploadRegion(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        if ((long) width * height > MAX_REGION_PIXELS) {
            this.texture.upload(this.bitmap);
            return;
        }

        ByteBuffer destination = this.uploadBuffer;
        if (destination == null || destination.capacity() < width * height) {
            destination = BufferUtils.createByteBuffer(width * height);
            this.uploadBuffer = destination;
        }

        destination.clear();
        ByteBuffer source = this.bitmap;
        int previousLimit = source.limit();

        for (int row = 0; row < height; row++) {
            int offset = (y + row) * size + x;
            source.limit(offset + width).position(offset);
            destination.put(source);
        }

        source.limit(previousLimit).position(0);
        destination.flip();

        Method upload = regionUpload();

        if (upload == null) {
            this.texture.upload(this.bitmap);
            return;
        }

        try {
            Object encoder = RenderSystem.getDevice().createCommandEncoder();

            if (upload.getParameterCount() == 9) {
                // 26.1：仍需显式给出像素格式
                upload.invoke(
                        encoder,
                        this.texture.getTexture(),
                        destination,
                        NativeImage.Format.LUMINANCE,
                        0, 0, x, y, width, height
                );
            } else {
                // 26.2：格式取自纹理自身
                upload.invoke(
                        encoder,
                        this.texture.getTexture(),
                        destination,
                        0, 0, x, y, width, height
                );
            }
        } catch (Throwable t) {
            // 反射调用失败时退回整张上传，宁可慢一点也不留错字。
            LOG.warn("按矩形上传字形失败，退回整张图集上传", t);
            this.texture.upload(this.bitmap);
        }
    }

    /**
     * 挑出当前版本 CommandEncoder 上「纹理 + ByteBuffer + 6 个 int」的那次上传调用。
     * 26.1 是 9 参（带 {@code NativeImage.Format}），26.2 是 8 参。
     */
    private static Method regionUpload() {
        if (!regionUploadResolved) {
            regionUploadResolved = true;

            for (Method method : CommandEncoder.class.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();

                if (!method.getName().equals("writeToTexture")) {
                    continue;
                }

                if (parameters.length == 8 && parameters[1] == ByteBuffer.class) {
                    // 优先 26.2 的形态；26.1 上不存在时继续往下找 9 参版本
                    regionUpload = method;
                    break;
                }

                if (parameters.length == 9 && parameters[1] == ByteBuffer.class && parameters[2].isEnum()) {
                    regionUpload = method;
                }
            }
        }

        return regionUpload;
    }

    public double getWidth(String string, int length) {
        boolean cacheable = length == string.length() && string.length() <= CACHE_MAX_LENGTH;
        if (cacheable && this.widthCache.containsKey(string)) {
            return this.widthCache.getDouble(string);
        }

        boolean ready = this.ensureGlyphs(string);

        double width = 0.0;

        for (int i = 0; i < length; i++) {
            CharData c = this.charMap.get(string.charAt(i));
            if (c == null) {
                // 理论上 ensureGlyphs 已经把字形补好了；真遇到字体里没有的字形时，
                // 按原版 Meteor 的做法退回空格宽度，而不是返回 0——Meteor 的界面布局
                // 算完一次就缓存住，宽度突然归零会让整屏控件永久错位。
                c = this.charMap.get(32);
            }

            if (c != null) {
                width += c.xAdvance;
            }
        }

        // 只有整串字形都齐了才缓存宽度，避免把「空格兜底」的临时值记下来。
        if (cacheable && ready) {
            this.widthCache.put(string, width);
        }

        return width;
    }

    public int getHeight() {
        return this.height;
    }

    /** 关闭打包上下文；纹理本身归 Font 所有，这里不碰。重复调用是安全的。 */
    public void close() {
        if (this.closed) {
            return;
        }

        this.closed = true;

        // stbtt_PackBegin 在原生堆上分配了节点，只靠 GC 收不走，必须显式结束。
        STBTruetype.stbtt_PackEnd(this.packContext);
        this.charMap.clear();
        this.loadedStrings.clear();
        this.widthCache.clear();
        this.uploadBuffer = null;
    }

    /**
     * 把字符串里缺的字形全部补进图集，返回整串字形是否都就绪。
     *
     * <p>补字是同步完成的，并且这里不再限制每帧补多少：Meteor 的界面只在第一帧算一次布局
     * 然后缓存住，如果量宽度时字形还没补完，控件宽度就会永久偏小、中文互相压字。
     * 相比这点一次性开销，布局正确更重要。</p>
     */
    private boolean ensureGlyphs(String s) {
        if (this.loadedStrings.contains(s)) {
            return true;
        }

        IntArrayList charPoints = null;

        for (int i = 0; i < s.length(); i++) {
            int cp = s.charAt(i);
            if (this.charMap.get(cp) == null) {
                if (charPoints == null) {
                    charPoints = new IntArrayList();
                }

                charPoints.add(cp);
            }
        }

        if (charPoints == null) {
            this.rememberLoaded(s);
            return true;
        }

        this.loadCharacter(charPoints);

        for (int i = 0; i < charPoints.size(); i++) {
            if (this.charMap.get(charPoints.getInt(i)) == null) {
                // 只有在图集被打满、stb 拒绝继续打包时才会走到这里。
                return false;
            }
        }

        this.rememberLoaded(s);
        return true;
    }

    private void rememberLoaded(String s) {
        if (s.length() > CACHE_MAX_LENGTH) {
            return;
        }

        if (this.loadedStrings.size() >= CACHE_LIMIT) {
            this.loadedStrings.clear();
            this.widthCache.clear();
        }

        this.loadedStrings.add(s);
    }

    public double render(MeshBuilder mesh, String string, double x, double y, Color color, double scale) {
        this.ensureGlyphs(string);

        y += (double) (this.ascent * this.scale) * scale;
        int length = string.length();
        mesh.ensureCapacity(length * 4, length * 6);

        for (int i = 0; i < length; i++) {
            int cp = string.charAt(i);
            CharData c = this.charMap.get(cp);
            if (c == null) {
                // 和 getWidth 保持一致：还没补完的字形按空格占位，而不是整串不画。
                c = this.charMap.get(32);
            }

            if (c != null) {
                mesh.quad(
                        mesh.vec2(x + (double) c.x0 * scale, y + (double) c.y0 * scale).vec2((double) c.u0, (double) c.v0).color(color).next(),
                        mesh.vec2(x + (double) c.x0 * scale, y + (double) c.y1 * scale).vec2((double) c.u0, (double) c.v1).color(color).next(),
                        mesh.vec2(x + (double) c.x1 * scale, y + (double) c.y1 * scale).vec2((double) c.u1, (double) c.v1).color(color).next(),
                        mesh.vec2(x + (double) c.x1 * scale, y + (double) c.y0 * scale).vec2((double) c.u1, (double) c.v0).color(color).next()
                );
                x += (double) c.xAdvance * scale;
            }
        }

        return x;
    }

    private record CharData(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, float xAdvance) {
    }
}
