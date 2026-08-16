package org.nigao.zhihuLite.web

import java.security.MessageDigest
import java.util.Base64

/**
 * Native Kotlin reimplementation of Zhihu's `x-zse-96` request signature.
 *
 * The original implementation ran Zhihu's obfuscated JavaScript (a custom stack VM) inside
 * a hidden WebView. That VM is a small bytecode interpreter: its "encrypt" step runs a
 * fixed 2345-opcode program against 143 decoded data chunks, with browser globals such as
 * `Date.now()` and `Math.random()` salted into the result. This class ports that interpreter
 * and program to Kotlin 1:1, so no WebView (or any JavaScript engine) is needed.
 *
 * The output is intentionally time/random salted, so every call produces a different but
 * valid signature - exactly like the original.
 */
object Zse96 {
    private const val ZSE_93 = "101_3_3.0"

    /**
     * Computes the value for the `x-zse-96` header.
     *
     * @param path the request path, e.g. `/api/v4/questions/123/feeds`
     * @param dC0 the decoded `d_c0` cookie value
     * @param nowMillis JS `Date.now()` equivalent, overridable for tests
     * @param randomValue JS `Math.random()` equivalent, overridable for tests
     */
    fun generate(
        path: String,
        dC0: String,
        nowMillis: () -> Long = { System.currentTimeMillis() },
        randomValue: () -> Double = { kotlin.random.Random.nextDouble() }
    ): String {
        val plain = ZSE_93 + "+" + path + "+" + dC0
        return "2.0_" + encrypt(md5Hex(plain), nowMillis, randomValue)
    }

    /** Runs Zhihu's encrypt program on [input] and returns the signature payload. */
    fun encrypt(
        input: String,
        nowMillis: () -> Long = { System.currentTimeMillis() },
        randomValue: () -> Double = { kotlin.random.Random.nextDouble() }
    ): String {
        val vm = JsVm(nowMillis, randomValue)
        vm.S = mutableListOf(
            encodeUriComponent(input),
            Zse96Tables.ZB.toMutableList(),
            Zse96Tables.ZM.toMutableList(),
            Zse96Tables.KEY.toMutableList(),
            Zse96Tables.ZK.toMutableList()
        )
        vm.run(Zse96Tables.PROGRAM.toMutableList(), Zse96Tables.START_IP, Zse96Tables.DATA.toMutableList())
        return vm.C[3] as String
    }

    private fun md5Hex(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** JavaScript `encodeURIComponent`, including its exact safe-character set. */
    private fun encodeUriComponent(s: String): String = buildString {
        for (ch in s) {
            val safe = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
                ch == '-' || ch == '_' || ch == '.' || ch == '!' || ch == '~' ||
                ch == '*' || ch == '\'' || ch == '(' || ch == ')'
            if (safe) {
                append(ch)
            } else {
                for (b in ch.toString().toByteArray(Charsets.UTF_8)) {
                    append('%')
                    append("%02X".format(b.toInt() and 0xFF))
                }
            }
        }
    }
}

/** Sentinel for JavaScript `undefined`. */
private object JsUndefined {
    override fun toString(): String = "undefined"
}

/** Exception carrying a JavaScript `throw` value, caught by the VM's own catch block. */
private class JsThrow(val value: Any?) : RuntimeException()

/** Callable JavaScript value (functions and constructors). */
private class JsFunc(val fn: (List<Any?>) -> Any?) {
    fun call(args: List<Any?>): Any? = fn(args)
}

/** Simple JavaScript object with dynamic members. */
private open class JsObj {
    private val members = HashMap<String, Any?>()

    open fun get(key: String): Any? = when (key) {
        "toString" -> JsFunc { "[object Object]" }
        else -> members[key] ?: JsUndefined
    }
    fun set(key: String, value: Any?) {
        members[key] = value
    }
}

private class JsMathObj(private val randomValue: () -> Double) : JsObj() {
    override fun get(key: String): Any? = when (key) {
        "random" -> JsFunc { randomValue() }
        "floor" -> JsFunc { args -> jsFloor(jsNum(args.getOrNull(0))) }
        "toString" -> JsFunc { "[object Math]" }
        else -> JsUndefined
    }
}

private class JsDateObj(private val nowMillis: () -> Long) : JsObj() {
    override fun get(key: String): Any? = when (key) {
        "now" -> JsFunc { nowMillis() }
        "toString" -> JsFunc { "function Date() { [native code] }" }
        else -> JsUndefined
    }

    fun construct(): Any? = JsObj().apply {
        set("getTime", JsFunc { nowMillis() })
    }
}

private object JsArrayCtor : JsObj() {
    override fun get(key: String): Any? = JsUndefined

    fun call(args: List<Any?>): Any? {
        if (args.size == 1 && args[0] is Number) {
            val n = jsNum(args[0]).toInt().coerceAtLeast(0)
            return MutableList(n) { JsUndefined }
        }
        return mutableListOf<Any?>()
    }

    fun construct(args: List<Any?>): Any? = call(args)
}

/** `__g` object: the AES-ish primitives used by the encrypt program. */
private class JsGObj(
    private val nowMillis: () -> Long,
    private val randomValue: () -> Double
) : JsObj() {
    override fun get(key: String): Any? = when (key) {
        "x" -> JsFunc { args -> gX(args.getOrNull(0), args.getOrNull(1)) }
        "r" -> JsFunc { args -> gR(args.getOrNull(0)) }
        "_encrypt" -> JsFunc { args ->
            Zse96.encrypt(jsToString(args.getOrNull(0)), nowMillis, randomValue)
        }
        "toString" -> JsFunc { "[object Object]" }
        else -> JsUndefined
    }
}

/**
 * The JS globals a VM instance can see. One instance per VM so repeated `eval`s of
 * `Date`/`Math`/`__g` return the same object, like the real browser globals.
 */
private class JsGlobals(
    val nowMillis: () -> Long,
    val randomValue: () -> Double
) {
    val date: JsDateObj by lazy { JsDateObj(nowMillis) }
    val math: JsMathObj by lazy { JsMathObj(randomValue) }
    val g: JsGObj by lazy { JsGObj(nowMillis, randomValue) }
}

/** Dummy browser object. The encrypt program touches these but never uses their values. */
private class JsBrowserObj(private val userAgent: Boolean) : JsObj() {
    override fun get(key: String): Any? = when {
        key == "userAgent" && userAgent -> UA_STRING
        key == "constructor" -> OBJECT_CONSTRUCTOR
        key == "toString" -> JsFunc { "[object Object]" }
        else -> JsUndefined
    }
}

/** Shared `Object` constructor sentinel (all browser shims use it). */
private val OBJECT_CONSTRUCTOR = JsObj()

private const val UA_STRING =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

/**
 * The JavaScript interpreter ported from Zhihu's payload (`l` + `l.prototype.O`).
 *
 * Field names mirror the original minified names; the `run` method is a direct translation
 * of the giant switch statement, with JavaScript value semantics applied via the helpers at
 * the bottom of this file.
 */
private class JsVm(
    private val nowMillis: () -> Long,
    private val randomValue: () -> Double
) {
    private val globals = JsGlobals(nowMillis, randomValue)

    var C: MutableList<Any?> = mutableListOf(0L, 0L, 0L, 0L)
    var ip: Any? = 0L
    var t: MutableList<Any?> = mutableListOf()
    var S: Any? = mutableListOf<Any?>()
    var h: MutableList<Any?> = mutableListOf()
    var i: Any? = mutableListOf<Any?>()
    var B: MutableList<Any?> = mutableListOf()
    var Q: Any? = false
    var G: MutableList<Any?> = mutableListOf()
    var D: MutableList<Any?> = mutableListOf()
    var w: Long = 1024
    var g: Any? = null
    var a: Any? = nowMillis()
    var e: Any? = 0L
    var T: Long = 255
    var V: Any? = null
    var U: () -> Any? = nowMillis
    var M: MutableList<Any?> = MutableList(32) { JsUndefined }

    // Dynamic fields created by the program.
    var b: Any? = JsUndefined
    var J: Any? = JsUndefined
    var W: Any? = JsUndefined
    var k: Any? = JsUndefined
    var c: Any? = JsUndefined
    var regI: Any? = JsUndefined
    var F: Any? = JsUndefined

    fun run(A: Any?, C0: Any?, s0: Any?) {
        // Locals shadowing instance fields, exactly like `var t, S, h, ...` in the original.
        var l_t: Any? = JsUndefined
        var l_S: Any? = JsUndefined
        var l_h: Any? = JsUndefined
        var l_i: Any? = JsUndefined
        var l_B: Any? = JsUndefined
        var l_Q: Any? = JsUndefined
        var l_G: Any? = JsUndefined
        var l_D: Any? = JsUndefined
        var l_w: Any? = JsUndefined
        var l_g: Any? = JsUndefined
        var l_a: Any? = JsUndefined
        var l_e: Any? = JsUndefined
        var l_E: Any? = JsUndefined
        var l_T: Any? = JsUndefined
        var l_r: Any? = JsUndefined
        var l_V: Any? = JsUndefined
        var l_U: Any? = JsUndefined
        var l_M: Any? = JsUndefined
        var l_O: Any? = JsUndefined
        var l_c: Any? = JsUndefined
        var l_I: Any? = JsUndefined

        // Function-scoped loop variables (var-hoisted in JS).
        var vF = 0L
        var vk = 0L
        var vW = 0L
        var vJ = 0L
        var vb = 0L
        var vn = 0L

        val str = StringBuilder()

        while (this.T < this.w) {
            try {
                when (this.T) {
                    27L -> {
                        jsSet(C, this.c, jsShr(jsGet(C, this.regI), jsGet(C, this.F)))
                        jsSet(M, 12, 35L)
                        this.T = this.T * (C.size + (if (jsTruthy(jsGet(M, 13))) 3 else 9)) + 1
                    }
                    34L -> {
                        jsSet(C, this.c, jsAnd(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = this.T * (jsLong(jsGet(M, 15)) - 6) + 12
                    }
                    41L -> {
                        jsSet(C, this.c, jsLe(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = 8 * this.T + 27
                    }
                    48L -> {
                        jsSet(C, this.c, !jsTruthy(jsGet(C, this.regI)))
                        this.T = 7 * this.T + 16
                    }
                    50L -> {
                        jsSet(C, this.c, jsOr(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = 6 * this.T + 52
                    }
                    57L -> {
                        jsSet(C, this.c, jsUshr(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = 7 * this.T - 47
                    }
                    64L -> {
                        jsSet(C, this.c, jsShl(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = 5 * this.T + 32
                    }
                    71L -> {
                        jsSet(C, this.c, jsXor(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = 6 * this.T - 74
                    }
                    78L -> {
                        jsSet(C, this.c, jsAnd(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = 4 * this.T + 40
                    }
                    80L -> {
                        jsSet(C, this.c, jsLt(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = 5 * this.T - 48
                    }
                    87L -> {
                        jsSet(C, this.c, jsNeg(jsGet(C, this.regI)))
                        this.T = 3 * this.T + 91
                    }
                    94L -> {
                        jsSet(C, this.c, jsGt(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = 4 * this.T - 24
                    }
                    101L -> {
                        jsSet(C, this.c, jsIn(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = 3 * this.T + 49
                    }
                    108L -> {
                        jsSet(C, this.c, jsTypeOf(jsGet(C, this.regI)))
                        this.T = 2 * this.T + 136
                    }
                    110L -> {
                        jsSet(C, this.c, !jsStrictEq(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T += 242
                    }
                    117L -> {
                        jsSet(C, this.c, jsLogicAnd(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = 3 * this.T + 1
                    }
                    124L -> {
                        jsSet(C, this.c, jsLogicOr(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T += 228
                    }
                    131L -> {
                        jsSet(C, this.c, jsGe(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = 3 * this.T - 41
                    }
                    138L -> {
                        jsSet(C, this.c, jsEq(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = 2 * this.T + 76
                    }
                    140L -> {
                        jsSet(C, this.c, jsMod(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T += 212
                    }
                    147L -> {
                        jsSet(C, this.c, jsDiv(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T += 205
                    }
                    154L -> {
                        jsSet(C, this.c, jsMul(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T += 198
                    }
                    161L -> {
                        jsSet(C, this.c, jsSub(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T += 191
                    }
                    168L -> {
                        jsSet(C, this.c, jsAdd(jsGet(C, this.regI), jsGet(C, this.F)))
                        this.T = 2 * this.T + 16
                    }
                    254L -> {
                        jsSet(C, this.c, jsEval(jsToString(l_i), globals))
                        this.T += 98
                    }
                    255L -> {
                        this.ip = jsLong(C0)
                        jsSet(M, 26, 52L)
                        this.T += 6
                    }
                    258L -> {
                        l_g = HashMap<String, Any?>()
                        for (f in 0L until jsLong(this.k)) {
                            vF = f
                            l_e = jsPop(this.i)
                            l_a = jsPop(this.i)
                            (l_g as MutableMap<String, Any?>)[jsToString(l_a)] = l_e
                        }
                        jsSet(C, this.W, l_g)
                        this.T += 94
                    }
                    261L -> {
                        this.D = (s0 as? MutableList<Any?>) ?: mutableListOf()
                        jsSet(M, 11, 68L)
                        this.T += 3
                    }
                    264L -> {
                        jsSet(M, 15, 16L)
                        this.T = if (A is String) 331 else 336
                    }
                    266L -> {
                        jsSetIndex(jsGet(C, this.regI), l_i, jsPop(this.i))
                        this.T += 86
                    }
                    278L -> {
                        if (jsStrictEq(l_i, "href")) {
                            jsSet(C, this.c, "http://127.0.0.1:5501/index.html")
                            this.T += 74
                        } else {
                            jsSet(C, this.c, jsIndex(jsGet(C, this.regI), l_i))
                            this.T += 74
                        }
                    }
                    283L -> {
                        jsSet(C, this.c, jsEval(Char(toInt32(jsGet(C, this.regI))).toString(), globals))
                    }
                    300L -> {
                        l_S = this.U()
                        jsSet(M, 0, 66L)
                        this.T += jsLong(jsGet(M, 11))
                    }
                    331L -> {
                        val decoded = Base64.getDecoder().decode(jsToString(A))
                        val bytes = decoded.map { it.toInt() and 0xFF }
                        val w = (bytes[0] shl 16) or (bytes[1] shl 8) or bytes[2]
                        var v = 3
                        while (v < w + 3) {
                            this.G.add(
                                ((bytes[v] shl 16) or (bytes[v + 1] shl 8) or bytes[v + 2]).toLong()
                            )
                            v += 3
                        }
                        var idx = w + 3
                        while (idx < bytes.size) {
                            val len = (bytes[idx] shl 8) or bytes[idx + 1]
                            val chunk = String(bytes.subList(idx + 2, idx + 2 + len).map { it.toByte() }.toByteArray(), Charsets.UTF_8)
                            this.D.add(chunk)
                            idx += len + 2
                        }
                        jsSet(M, 21, 8L)
                        this.T += 21
                    }
                    336L -> {
                        this.G = A as MutableList<Any?>
                        this.D = s0 as MutableList<Any?>
                        jsSet(M, 18, 134L)
                        this.T += jsLong(jsGet(M, 15))
                    }
                    344L -> {
                        this.T = 3 * this.T - 8
                    }
                    350L -> {
                        l_U = 66L
                        l_M = mutableListOf<Any?>()
                        l_I = jsGet(this.D, this.k)
                        val chunk = jsToString(l_I)
                        for (i in chunk.indices) {
                            vW = i.toLong()
                            val code = 24 xor chunk[i].code xor toInt32(l_U)
                            (l_M as MutableList<Any?>).add(Char(code).toString())
                            l_U = (24 xor chunk[i].code xor toInt32(l_U)).toLong()
                        }
                        val parts = jsJoin(l_M, "").split('|')
                        l_r = jsParseInt(parts.getOrNull(1) ?: "")
                        val end = jsSub(jsList(this.i).size.toLong(), l_r)
                        jsSet(C, this.W, jsSlice(this.i, end, JsUndefined))
                        this.i = jsSlice(this.i, 0L, end)
                        this.T += 2
                    }
                    352L -> {
                        this.e = jsGet(this.G, this.ip)
                        this.ip = jsAdd(this.ip, 1L)
                        this.T -= jsLong(jsGet(M, 26))
                    }
                    360L -> {
                        this.a = l_S
                        this.T += jsLong(jsGet(M, 0))
                    }
                    368L -> {
                        this.T -= 8
                    }
                    380L -> {
                        jsList(this.i).add((16383 and toInt32(this.e)).toLong())
                        this.T -= 28
                    }
                    400L -> {
                        jsList(this.i).add(jsGet(this.S, (16383 and toInt32(this.e)).toLong()))
                        this.T -= 48
                    }
                    408L -> {
                        this.T -= 64
                    }
                    413L -> {
                        val idx = (toInt32(this.e) shr 15) and 7
                        val value = if (((toInt32(this.e) shr 18) and 1) == 0) {
                            (32767 and toInt32(this.e)).toLong()
                        } else {
                            jsGet(this.S, (32767 and toInt32(this.e)).toLong())
                        }
                        jsSet(C, idx.toLong(), value)
                        this.T -= 61
                    }
                    418L -> {
                        jsSet(this.S, (65535 and toInt32(this.e)).toLong(), jsGet(C, ((toInt32(this.e) shr 16) and 7).toLong()))
                        this.T -= 66
                    }
                    423L -> {
                        this.c = ((toInt32(this.e) shr 16) and 7).toLong()
                        this.regI = ((toInt32(this.e) shr 13) and 7).toLong()
                        this.F = ((toInt32(this.e) shr 10) and 7).toLong()
                        this.J = (1023 and toInt32(this.e)).toLong()
                        this.T -= 255 + 6 * jsLong(this.J) + (jsLong(this.J) % 5)
                    }
                    426L -> {
                        this.T += 5 * (toInt32(this.e) shr 19) - 18
                    }
                    428L -> {
                        this.W = ((toInt32(this.e) shr 16) and 7).toLong()
                        this.k = (65535 and toInt32(this.e)).toLong()
                        this.t.add(this.ip)
                        this.h.add(this.S)
                        this.ip = jsLong(jsGet(C, this.W))
                        this.S = mutableListOf<Any?>()
                        for (j in 0L until jsLong(this.k)) {
                            vJ = j
                            (this.S as MutableList<Any?>).add(0, jsPop(this.i))
                        }
                        this.B.add(this.i)
                        this.i = mutableListOf<Any?>()
                        this.T -= 76
                    }
                    433L -> {
                        this.ip = jsPop(this.t)
                        this.S = jsPop(this.h)
                        this.i = jsPop(this.B)
                        this.T -= 81
                    }
                    438L -> {
                        this.Q = jsGet(C, ((toInt32(this.e) shr 16) and 7).toLong())
                        this.T -= 86
                    }
                    440L -> {
                        l_U = 66L
                        l_M = mutableListOf<Any?>()
                        l_I = jsGet(this.D, (16383 and toInt32(this.e)).toLong())
                        val chunk = jsToString(l_I)
                        for (i in chunk.indices) {
                            vb = i.toLong()
                            val code = 24 xor chunk[i].code xor toInt32(l_U)
                            str.append(Char(code))
                            (l_M as MutableList<Any?>).add(Char(code).toString())
                            l_U = (24 xor chunk[i].code xor toInt32(l_U)).toLong()
                        }
                        val joined = jsJoin(l_M, "")
                        if (joined.contains("href")) {
                            // anti-debug decoy in the original
                        }
                        val parts = joined.split('|').toMutableList()
                        l_M = parts
                        l_O = jsParseInt(jsToString(jsShift(parts)))
                        val o = jsLong(l_O)
                        val pushed: Any? = when (o) {
                            0L -> parts.joinToString("|")
                            1L -> if (parts.joinToString(",").indexOf('.') != -1) {
                                jsParseInt(parts.joinToString(","))
                            } else {
                                jsParseFloat(parts.joinToString(","))
                            }
                            2L -> jsEval(parts.joinToString("|"), globals)
                            3L -> null
                            else -> JsUndefined
                        }
                        jsList(this.i).add(pushed)
                        this.T -= 88
                    }
                    443L -> {
                        this.b = ((toInt32(this.e) shr 2) and 65535).toLong()
                        this.J = (3 and toInt32(this.e)).toLong()
                        val j = jsLong(this.J)
                        when (j) {
                            0L -> this.ip = jsLong(this.b)
                            1L -> if (jsTruthy(this.Q)) this.ip = jsLong(this.b)
                            else -> if (!(j == 2L && jsTruthy(this.Q))) this.ip = jsLong(this.b)
                        }
                        this.g = null
                        this.T -= 91
                    }
                    445L -> {
                        jsList(this.i).add(jsGet(C, ((toInt32(this.e) shr 14) and 7).toLong()))
                        this.T -= 93
                    }
                    448L -> {
                        this.W = ((toInt32(this.e) shr 16) and 7).toLong()
                        this.k = ((toInt32(this.e) shr 2) and 4095).toLong()
                        this.J = (3 and toInt32(this.e)).toLong()
                        l_Q = if (jsLong(this.J) == 1L) jsPop(this.i) else false
                        val kk = jsLong(this.k)
                        l_G = jsSlice(this.i, (jsList(this.i).size - kk).toLong(), JsUndefined)
                        this.i = jsSlice(this.i, 0L, (jsList(this.i).size - kk).toLong())
                        val g = l_G as MutableList<Any?>
                        l_c = if (g.size > 2) 3L else g.size.toLong()
                        this.T += 6 * jsLong(this.J) + 1 + 10 * jsLong(l_c)
                    }
                    449L -> {
                        jsSet(C, 3L, jsCall(jsGet(C, this.W), emptyList()))
                        this.T -= 97 - (l_G as MutableList<Any?>).size
                    }
                    453L -> {
                        val bb = (toInt32(this.e) shr 17) and 3
                        this.T = when (bb) {
                            0 -> 445L
                            1 -> 380L
                            2 -> 400L
                            else -> 440L
                        }
                    }
                    455L -> {
                        jsSet(C, 3L, jsCall(jsIndex(jsGet(C, this.W), l_Q), emptyList()))
                        this.T -= 103 + (l_G as MutableList<Any?>).size
                    }
                    458L -> {
                        this.J = ((toInt32(this.e) shr 17) and 3).toLong()
                        this.c = ((toInt32(this.e) shr 14) and 7).toLong()
                        this.regI = ((toInt32(this.e) shr 11) and 7).toLong()
                        l_i = jsPop(this.i)
                        this.T -= 12 * jsLong(this.J) + 180
                    }
                    459L -> {
                        jsSet(C, 3L, jsCall(jsGet(C, this.W), listOf((l_G as MutableList<Any?>)[0])))
                        this.T -= 100 + 7 * (l_G as MutableList<Any?>).size
                    }
                    461L -> {
                        jsSet(C, 3L, jsNew(jsGet(C, this.W), emptyList()))
                        this.T -= 109 - (l_G as MutableList<Any?>).size
                    }
                    463L -> {
                        l_U = 66L
                        l_M = mutableListOf<Any?>()
                        l_I = jsGet(this.D, (65535 and toInt32(this.e)).toLong())
                        val chunk = jsToString(l_I)
                        for (i in chunk.indices) {
                            vn = i.toLong()
                            val code = 24 xor chunk[i].code xor toInt32(l_U)
                            (l_M as MutableList<Any?>).add(Char(code).toString())
                            l_U = (24 xor chunk[i].code xor toInt32(l_U)).toLong()
                        }
                        val joined = jsJoin(l_M, "")
                        if (joined.contains("headless")) {
                            // anti-debug decoy in the original
                        }
                        val parts = joined.split('|').toMutableList()
                        l_M = parts
                        l_O = jsParseInt(jsToString(jsShift(parts)))
                        this.T += 10 * jsLong(l_O) + 3
                    }
                    465L -> {
                        jsSet(C, 3L, jsCall(jsIndex(jsGet(C, this.W), l_Q), listOf((l_G as MutableList<Any?>)[0])))
                        this.T -= 13 * (l_G as MutableList<Any?>).size + 100
                    }
                    466L -> {
                        jsSet(C, ((toInt32(this.e) shr 16) and 7).toLong(), jsJoin(l_M, "|"))
                        this.T -= 114 * (l_M as MutableList<Any?>).size
                    }
                    468L -> {
                        this.g = (65535 and toInt32(this.e)).toLong()
                        this.T -= 116
                    }
                    469L -> {
                        val g = l_G as MutableList<Any?>
                        jsSet(C, 3L, jsCall(jsGet(C, this.W), listOf(g[0], g[1])))
                        this.T -= 119 - g.size
                    }
                    471L -> {
                        jsSet(C, 3L, jsNew(jsGet(C, this.W), listOf((l_G as MutableList<Any?>)[0])))
                        this.T -= 118 + (l_G as MutableList<Any?>).size
                    }
                    473L -> {
                        throw JsThrow(jsGet(C, ((toInt32(this.e) shr 16) and 7).toLong()))
                    }
                    475L -> {
                        val g = l_G as MutableList<Any?>
                        jsSet(C, 3L, jsCall(jsIndex(jsGet(C, this.W), l_Q), listOf(g[0], g[1])))
                        this.T -= 123
                    }
                    476L -> {
                        val joined = jsJoin(l_M, "")
                        val value: Any? = if (joined.indexOf('.') != -1) {
                            jsParseInt(joined)
                        } else {
                            jsParseFloat(joined)
                        }
                        jsSet(C, ((toInt32(this.e) shr 16) and 7).toLong(), value)
                        this.T -= if (jsNum(jsGet(M, 21)) < 10.0) 124 else 126
                    }
                    478L -> {
                        l_t = jsConcat(mutableListOf(0L), jsSpread(this.S))
                        this.V = (65535 and toInt32(this.e)).toLong()
                        l_h = this
                        val capturedT = l_t
                        val capturedH = l_h as JsVm
                        jsSet(C, 3L, JsFunc { args ->
                            val te = JsVm(nowMillis, randomValue)
                            te.S = capturedT as MutableList<Any?>
                            jsSet(te.S, 0, args.getOrNull(0))
                            te.run(capturedH.G, capturedH.V, capturedH.D)
                            te.C[3]
                        })
                        this.T -= 126
                    }
                    479L -> {
                        jsSet(C, 3L, jsCall(jsGet(C, this.W), l_G as MutableList<Any?>))
                        jsSet(M, 3, 168L)
                        this.T -= if (jsTruthy(jsGet(M, 9))) 127 else 128
                    }
                    481L -> {
                        val g = l_G as MutableList<Any?>
                        jsSet(C, 3L, jsNew(jsGet(C, this.W), listOf(g[0], g[1])))
                        this.T -= 10 * g.size + 109
                    }
                    483L -> {
                        this.J = ((toInt32(this.e) shr 15) and 15).toLong()
                        this.W = ((toInt32(this.e) shr 12) and 7).toLong()
                        this.k = (4095 and toInt32(this.e)).toLong()
                        this.T = if (jsLong(this.J) == 0L) 258 else 350
                    }
                    485L -> {
                        jsSet(C, 3L, jsCall(jsIndex(jsGet(C, this.W), l_Q), l_G as MutableList<Any?>))
                        this.T -= if (jsNum(jsGet(M, 15)) % 2.0 == 1.0) 143 else 133
                    }
                    486L -> {
                        jsSet(C, ((toInt32(this.e) shr 16) and 7).toLong(), jsEval(jsJoin(l_M, ""), globals))
                        this.T -= jsLong(jsGet(M, 18))
                    }
                    491L -> {
                        jsSet(C, 3L, jsNew(jsCall(jsGet(C, this.W), l_G as MutableList<Any?>), emptyList()))
                        this.T -= if (jsNum(jsGet(M, 8)) / jsNum(jsGet(M, 1)) < 10.0) 139 else 130
                    }
                    496L -> {
                        jsSet(C, ((toInt32(this.e) shr 16) and 7).toLong(), null)
                        this.T -= if (10.0 < jsNum(jsGet(M, 5)) - jsNum(jsGet(M, 3))) 160 else 144
                    }
                    506L -> {
                        jsSet(C, ((toInt32(this.e) shr 16) and 7).toLong(), JsUndefined)
                        this.T -= if (jsNum(jsGet(M, 18)) % jsNum(jsGet(M, 12)) == 1.0) 154 else 145
                    }
                    else -> this.T = this.w
                }
            } catch (e: JsThrow) {
                if (jsTruthy(this.g)) this.ip = jsLong(this.g)
                this.T -= 114
            }
        }
    }
}

// ---------------------------------------------------------------------------
// JavaScript value semantics
// ---------------------------------------------------------------------------

private fun jsNum(v: Any?): Double = when (v) {
    is Long -> v.toDouble()
    is Double -> v
    is Int -> v.toDouble()
    is Boolean -> if (v) 1.0 else 0.0
    is String -> if (v.isBlank()) 0.0 else v.toDoubleOrNull() ?: Double.NaN
    null, JsUndefined -> Double.NaN
    else -> Double.NaN
}

/** JS ToInt32: wraps modulo 2^32 and signs. */
private fun toInt32(v: Any?): Int {
    val d = jsNum(v)
    if (d.isNaN() || d.isInfinite()) return 0
    var x = d % 4294967296.0
    if (x < 0) x += 4294967296.0
    if (x >= 2147483648.0) x -= 4294967296.0
    return x.toInt()
}

private fun jsLong(v: Any?): Long {
    val d = jsNum(v)
    return if (d.isNaN()) 0L else d.toLong()
}

private fun jsTruthy(v: Any?): Boolean = when (v) {
    null, JsUndefined -> false
    is Boolean -> v
    is Long -> v != 0L
    is Double -> !v.isNaN() && v != 0.0
    is Int -> v != 0
    is String -> v.isNotEmpty()
    else -> true
}

private fun jsToString(v: Any?): String = when (v) {
    null -> "null"
    JsUndefined -> "undefined"
    is String -> v
    is Boolean -> if (v) "true" else "false"
    is Long -> v.toString()
    is Double -> {
        if (v.isNaN()) "NaN"
        else if (v == Math.floor(v) && kotlin.math.abs(v) < 1e21) v.toLong().toString()
        else v.toString()
    }
    is Int -> v.toString()
    is List<*> -> jsJoin(v, JsUndefined)
    is MutableMap<*, *> -> "[object Object]"
    is JsMathObj -> "[object Math]"
    is JsDateObj -> "function Date() { [native code] }"
    is JsArrayCtor -> "function Array() { [native code] }"
    is JsGObj -> "[object Object]"
    is JsObj -> "[object Object]"
    else -> "[object Object]"
}

private fun jsTypeOf(v: Any?): String = when (v) {
    JsUndefined -> "undefined"
    null -> "object"
    is Boolean -> "boolean"
    is Long, is Double, is Int -> "number"
    is String -> "string"
    is JsFunc -> "function"
    else -> "object"
}

private fun jsFloor(d: Double): Any? {
    val f = Math.floor(d)
    return if (f == Math.floor(f) && !f.isInfinite()) f.toLong() else f
}

private fun jsAdd(a: Any?, b: Any?): Any? {
    if (a is String || b is String || a is List<*> || b is List<*> ||
        a is MutableMap<*, *> || b is MutableMap<*, *> || a is JsObj || b is JsObj
    ) {
        return jsToString(a) + jsToString(b)
    }
    val x = jsNum(a)
    val y = jsNum(b)
    if (x.isNaN() || y.isNaN()) return Double.NaN
    val r = x + y
    return if (r == Math.floor(r) && kotlin.math.abs(r) < Long.MAX_VALUE.toDouble()) r.toLong() else r
}

private fun jsSub(a: Any?, b: Any?): Any? = jsNumResult(jsNum(a) - jsNum(b))
private fun jsMul(a: Any?, b: Any?): Any? = jsNumResult(jsNum(a) * jsNum(b))
private fun jsDiv(a: Any?, b: Any?): Any? = jsNum(jsNum(a) / jsNum(b))
private fun jsMod(a: Any?, b: Any?): Any? = jsNum(jsNum(a) % jsNum(b))

private fun jsNumResult(d: Double): Any? =
    if (d.isNaN()) Double.NaN
    else if (d == Math.floor(d) && kotlin.math.abs(d) < Long.MAX_VALUE.toDouble()) d.toLong()
    else d

private fun jsNeg(v: Any?): Any? = jsNumResult(-jsNum(v))

private fun jsAnd(a: Any?, b: Any?): Any? = (toInt32(a) and toInt32(b)).toLong()
private fun jsOr(a: Any?, b: Any?): Any? = (toInt32(a) or toInt32(b)).toLong()
private fun jsXor(a: Any?, b: Any?): Any? = (toInt32(a) xor toInt32(b)).toLong()
private fun jsShl(a: Any?, b: Any?): Any? = (toInt32(a) shl toInt32(b)).toLong()
private fun jsShr(a: Any?, b: Any?): Any? = (toInt32(a) shr toInt32(b)).toLong()
private fun jsUshr(a: Any?, b: Any?): Any? = (toInt32(a) ushr toInt32(b)).toLong()

private fun jsLt(a: Any?, b: Any?): Boolean {
    if (a is String && b is String) {
        return a < b
    }
    val x = jsNum(a)
    val y = jsNum(b)
    if (x.isNaN() || y.isNaN()) return false
    return x < y
}

private fun jsLe(a: Any?, b: Any?): Boolean {
    if (a is String && b is String) {
        return a <= b
    }
    val x = jsNum(a)
    val y = jsNum(b)
    if (x.isNaN() || y.isNaN()) return false
    return x <= y
}

private fun jsGt(a: Any?, b: Any?): Boolean {
    if (a is String && b is String) {
        return a > b
    }
    val x = jsNum(a)
    val y = jsNum(b)
    if (x.isNaN() || y.isNaN()) return false
    return x > y
}

private fun jsGe(a: Any?, b: Any?): Boolean {
    if (a is String && b is String) {
        return a >= b
    }
    val x = jsNum(a)
    val y = jsNum(b)
    if (x.isNaN() || y.isNaN()) return false
    return x >= y
}

private fun jsEq(a: Any?, b: Any?): Boolean {
    if (a == null || b == null) return (a == null) && (b == null) || (a == null && b === JsUndefined) || (a === JsUndefined && b == null)
    if (a === JsUndefined || b === JsUndefined) return a === b
    if (a is Boolean) return jsEq(if (a) 1L else 0L, b)
    if (b is Boolean) return jsEq(a, if (b) 1L else 0L)
    if (a is String && b is String) return a == b
    if (a is String) return jsEq(jsNum(a), b)
    if (b is String) return jsEq(a, jsNum(b))
    if (a is Long || a is Double || a is Int) {
        if (b is Long || b is Double || b is Int) return jsNum(a) == jsNum(b)
    }
    return a === b
}

private fun jsStrictEq(a: Any?, b: Any?): Boolean {
    if ((a is Long || a is Double || a is Int) && (b is Long || b is Double || b is Int)) {
        return jsNum(a) == jsNum(b)
    }
    return a === b
}

private fun jsLogicAnd(a: Any?, b: Any?): Any? = if (!jsTruthy(a)) a else b
private fun jsLogicOr(a: Any?, b: Any?): Any? = if (jsTruthy(a)) a else b

private fun jsIn(key: Any?, obj: Any?): Boolean = when (obj) {
    is List<*> -> {
        val idx = key.toString().toIntOrNull() ?: return false
        idx in obj.indices
    }
    is MutableMap<*, *> -> obj.containsKey(key)
    is JsObj -> obj.get(key.toString()) !== JsUndefined
    else -> false
}

private fun jsGet(coll: Any?, idx: Any?): Any? {
    val key = jsToString(idx)
    return when (coll) {
        is String -> when (key) {
            "length" -> coll.length
            else -> {
                val i = key.toIntOrNull()
                if (i != null && i in coll.indices) coll[i].toString()
                else stringMethod(coll, key) ?: JsUndefined
            }
        }
        is List<*> -> when (key) {
            "length" -> coll.size
            else -> {
                val i = key.toIntOrNull()
                if (i != null && i >= 0 && i < coll.size) coll[i]
                else arrayMethod(coll, key) ?: JsUndefined
            }
        }
        is JsObj -> coll.get(key)
        is MutableMap<*, *> ->
            if (key == "toString") JsFunc { "[object Object]" } else (coll[key] ?: JsUndefined)
        else -> JsUndefined
    }
}

/** String prototype methods that the program can invoke through the VM. */
private fun stringMethod(s: String, key: String): JsFunc? = when (key) {
    "charAt" -> JsFunc { args ->
        val i = jsLong(args.getOrNull(0)).toInt()
        if (i in s.indices) s[i].toString() else ""
    }
    "charCodeAt" -> JsFunc { args ->
        val i = jsLong(args.getOrNull(0)).toInt()
        if (i in s.indices) s[i].code.toLong() else Double.NaN
    }
    "indexOf" -> JsFunc { args ->
        s.indexOf(jsToString(args.getOrNull(0))).toLong()
    }
    "includes" -> JsFunc { args ->
        s.contains(jsToString(args.getOrNull(0)))
    }
    "slice" -> JsFunc { args ->
        jsStringSlice(s, args.getOrNull(0), args.getOrNull(1))
    }
    "substring" -> JsFunc { args ->
        jsStringSubstring(s, args.getOrNull(0), args.getOrNull(1))
    }
    "split" -> JsFunc { args ->
        jsStringSplit(s, args.getOrNull(0))
    }
    "concat" -> JsFunc { args -> s + args.joinToString("") { jsToString(it) } }
    "toLowerCase" -> JsFunc { s.lowercase() }
    "toUpperCase" -> JsFunc { s.uppercase() }
    "trim" -> JsFunc { s.trim() }
    "toString" -> JsFunc { s }
    "replace" -> JsFunc { args ->
        val search = jsToString(args.getOrNull(0))
        val repl = jsToString(args.getOrNull(1))
        if (search.isEmpty()) s else s.replace(search, repl)
    }
    else -> null
}

/** Array prototype methods that the program can invoke through the VM. */
private fun arrayMethod(list: List<Any?>, key: String): JsFunc? = when (key) {
    "push" -> JsFunc { args ->
        val ml = list as MutableList<Any?>
        ml.addAll(args)
        ml.size.toLong()
    }
    "pop" -> JsFunc { jsPop(list as MutableList<*>) }
    "shift" -> JsFunc { jsShift(list as MutableList<*>) }
    "unshift" -> JsFunc { args ->
        val ml = list as MutableList<Any?>
        ml.addAll(0, args)
        ml.size.toLong()
    }
    "slice" -> JsFunc { args ->
        jsSlice(list as MutableList<Any?>, args.getOrNull(0), args.getOrNull(1))
    }
    "concat" -> JsFunc { args ->
        val ml = list.toMutableList()
        for (a in args) {
            if (a is List<*>) ml.addAll(a) else ml.add(a)
        }
        ml
    }
    "join" -> JsFunc { args -> jsJoin(list, args.getOrNull(0)) }
    "indexOf" -> JsFunc { args ->
        list.indexOfFirst { jsStrictEq(it, args.getOrNull(0)) }.toLong()
    }
    "includes" -> JsFunc { args -> list.any { jsStrictEq(it, args.getOrNull(0)) } }
    "toString" -> JsFunc { jsJoin(list, ",") }
    "reverse" -> JsFunc {
        val ml = list as MutableList<Any?>
        ml.reverse()
        ml
    }
    else -> null
}

private fun jsSet(coll: Any?, idx: Any?, value: Any?) {
    when (coll) {
        is MutableList<*> -> {
            val list = coll as MutableList<Any?>
            val i = jsIndexInt(idx) ?: return
            while (list.size <= i) list.add(JsUndefined)
            list[i] = value
        }
        is JsObj -> coll.set(jsToString(idx), value)
        is MutableMap<*, *> -> {
            val map = coll as MutableMap<Any?, Any?>
            map[jsToString(idx)] = value
        }
    }
}

private fun jsIndex(obj: Any?, key: Any?): Any? = jsGet(obj, key)

private fun jsSetIndex(obj: Any?, key: Any?, value: Any?) {
    jsSet(obj, key, value)
}

private fun jsIndexInt(v: Any?): Int? = when (v) {
    is Long -> v.toInt()
    is Int -> v
    is Double -> v.toInt()
    is String -> v.toIntOrNull()
    else -> null
}

private fun jsPop(list: Any?): Any? = when (list) {
    is MutableList<*> -> if (list.isEmpty()) JsUndefined else list.removeAt(list.size - 1)
    else -> throw JsThrow(JsUndefined)
}

private fun jsShift(list: Any?): Any? = when (list) {
    is MutableList<*> -> if (list.isEmpty()) JsUndefined else list.removeAt(0)
    else -> throw JsThrow(JsUndefined)
}

private fun jsList(v: Any?): MutableList<Any?> =
    v as? MutableList<Any?> ?: throw JsThrow(JsUndefined)

private fun jsSlice(list: Any?, start: Any?, end: Any?): MutableList<Any?> {
    val l = jsList(list)
    val n = l.size
    var s = toInt32(start)
    if (s < 0) s = (n + s).coerceAtLeast(0)
    val e = if (end === JsUndefined || end == null) n else toInt32(end)
    val ee = if (e < 0) (n + e).coerceAtLeast(0) else e
    return l.subList(s.coerceAtMost(n), ee.coerceAtMost(n)).toMutableList()
}

private fun jsStringSlice(s: String, start: Any?, end: Any?): String {
    val n = s.length
    var st = toInt32(start)
    if (st < 0) st = (n + st).coerceAtLeast(0)
    val e = if (end === JsUndefined || end == null) n else toInt32(end)
    val ee = if (e < 0) (n + e).coerceAtLeast(0) else e
    return s.substring(st.coerceAtMost(n), ee.coerceAtMost(n))
}

private fun jsStringSubstring(s: String, start: Any?, end: Any?): String {
    var st = jsLong(start).toInt().coerceAtLeast(0)
    var e = if (end === JsUndefined || end == null) s.length else jsLong(end).toInt().coerceAtLeast(0)
    st = st.coerceAtMost(s.length)
    e = e.coerceAtMost(s.length)
    if (st > e) {
        val t = st
        st = e
        e = t
    }
    return s.substring(st, e)
}

private fun jsStringSplit(s: String, sep: Any?): MutableList<Any?> {
    if (sep === JsUndefined || sep == null) return mutableListOf(s)
    val sp = jsToString(sep)
    if (sp.isEmpty()) return s.map { it.toString() }.toMutableList()
    return s.split(sp).toMutableList()
}

private fun jsConcat(a: MutableList<Any?>, b: List<Any?>): MutableList<Any?> =
    (a + b).toMutableList()

private fun jsSpread(list: Any?): MutableList<Any?> = jsList(list).toMutableList()

private fun jsJoin(v: Any?, sep: Any?): String {
    val list = v as? List<*> ?: throw JsThrow(JsUndefined)
    val s = if (sep === JsUndefined || sep == null) "," else jsToString(sep)
    return list.joinToString(s) { jsToString(it) }
}

private fun jsParseInt(s: String): Any? {
    var idx = 0
    while (idx < s.length && s[idx].isWhitespace()) idx++
    var sign = 1L
    if (idx < s.length && (s[idx] == '+' || s[idx] == '-')) {
        if (s[idx] == '-') sign = -1L
        idx++
    }
    var radix = 10
    var digits = ""
    if (idx + 1 < s.length && s[idx] == '0' && (s[idx + 1] == 'x' || s[idx + 1] == 'X')) {
        radix = 16
        idx += 2
    }
    while (idx < s.length && s[idx].digitToIntOrNull(radix) != null) {
        digits += s[idx]
        idx++
    }
    if (digits.isEmpty()) return Double.NaN
    return sign * digits.toLong(radix)
}

private fun jsParseFloat(s: String): Any? {
    var idx = 0
    while (idx < s.length && s[idx].isWhitespace()) idx++
    val start = idx
    if (idx < s.length && (s[idx] == '+' || s[idx] == '-')) idx++
    var sawDigit = false
    while (idx < s.length && s[idx].isDigit()) {
        idx++
        sawDigit = true
    }
    if (idx < s.length && s[idx] == '.') {
        idx++
        while (idx < s.length && s[idx].isDigit()) {
            idx++
            sawDigit = true
        }
    }
    if (sawDigit && idx < s.length && (s[idx] == 'e' || s[idx] == 'E')) {
        var j = idx + 1
        if (j < s.length && (s[j] == '+' || s[j] == '-')) j++
        val eStart = j
        while (j < s.length && s[j].isDigit()) j++
        if (j > eStart) idx = j
    }
    if (!sawDigit) return Double.NaN
    return jsNumResult(s.substring(start, idx).toDouble())
}

private fun jsCall(fn: Any?, args: List<Any?>): Any? = when (fn) {
    is JsFunc -> fn.call(args)
    is JsArrayCtor -> fn.call(args)
    is JsDateObj -> fn.construct()
    else -> JsUndefined
}

private fun jsNew(fn: Any?, args: List<Any?>): Any? = when (fn) {
    is JsArrayCtor -> fn.construct(args)
    is JsDateObj -> fn.construct()
    is JsFunc -> fn.call(args)
    else -> JsUndefined
}

private val browserObjects = mapOf(
    "window" to JsBrowserObj(false),
    "document" to JsBrowserObj(false),
    "navigator" to JsBrowserObj(true),
    "location" to JsBrowserObj(false),
    "history" to JsBrowserObj(false),
    "screen" to JsBrowserObj(false)
)

private fun jsEval(code: String, globals: JsGlobals): Any? = when (code) {
    "Date" -> globals.date
    "Math" -> globals.math
    "Array" -> JsArrayCtor
    "__g" -> globals.g
    else -> browserObjects[code] ?: throw JsThrow(JsUndefined)
}

// ---------------------------------------------------------------------------
// __g primitives: the block cipher building blocks
// ---------------------------------------------------------------------------

private fun gX(tt: Any?, te: Any?): MutableList<Any?> {
    val input = tt as List<Any?>
    var key: List<Any?> = te as List<Any?>
    val result = mutableListOf<Any?>()
    var remaining = input.size
    var ta = 0
    while (0 < remaining) {
        val block = input.slice(16 * ta until 16 * (ta + 1))
        val tc = MutableList(16) { i -> jsXor(block[i], key[i]) }
        key = gR(tc)
        result.addAll(key)
        ta++
        remaining -= 16
    }
    return result
}

private fun gR(tt: Any?): MutableList<Long> {
    val input = tt as List<Any?>
    val te = LongArray(16)
    val tr = LongArray(36)
    tr[0] = jsB(input, 0)
    tr[1] = jsB(input, 4)
    tr[2] = jsB(input, 8)
    tr[3] = jsB(input, 12)
    for (ti in 0 until 32) {
        val ta = gG(tr[ti + 1] xor tr[ti + 2] xor tr[ti + 3] xor Zse96Tables.ZK[ti])
        tr[ti + 4] = tr[ti] xor ta
    }
    jsI(tr[35], te, 0)
    jsI(tr[34], te, 4)
    jsI(tr[33], te, 8)
    jsI(tr[32], te, 12)
    return te.toMutableList()
}

private fun jsI(tt: Long, te: LongArray, tr: Int) {
    te[tr] = (255 and (tt ushr 24).toInt()).toLong()
    te[tr + 1] = (255 and (tt ushr 16).toInt()).toLong()
    te[tr + 2] = (255 and (tt ushr 8).toInt()).toLong()
    te[tr + 3] = (255 and tt.toInt()).toLong()
}

private fun jsB(tt: List<Any?>, te: Int): Long {
    val b0 = toInt32(tt[te])
    val b1 = toInt32(tt[te + 1])
    val b2 = toInt32(tt[te + 2])
    val b3 = toInt32(tt[te + 3])
    return ((b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3).toLong()
}

private fun jsQ(tt: Long, te: Int): Long {
    val x = tt.toInt()
    return ((x shl te) or (x ushr (32 - te))).toLong()
}

private fun gG(tt: Long): Long {
    val te = LongArray(4)
    jsI(tt, te, 0)
    val tr = LongArray(4)
    tr[0] = Zse96Tables.ZB[255 and te[0].toInt()]
    tr[1] = Zse96Tables.ZB[255 and te[1].toInt()]
    tr[2] = Zse96Tables.ZB[255 and te[2].toInt()]
    tr[3] = Zse96Tables.ZB[255 and te[3].toInt()]
    val ti = jsB(tr.toList(), 0)
    return ti xor jsQ(ti, 2) xor jsQ(ti, 10) xor jsQ(ti, 18) xor jsQ(ti, 24)
}
