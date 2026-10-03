import com.astradownloader.LinkResolver

fun main() {
    test("https://pan.quark.cn/s/100de2a644df")
    test("https://sai.gong.banhbao.im/download/lvcha_310_abjlvcha.apk")
    test("https://speed.hetzner.de/100MB.bin")
}

fun test(url: String) {
    val r = LinkResolver.resolve(url)
    println("INPUT: $url")
    println("  -> $r")
    println()
}