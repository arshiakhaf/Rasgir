package ir.rasgir.test

import java.io.File
import java.math.BigInteger
import kotlin.math.abs

/** Tiny assertion harness (JUnit-free so tests run on the offline JDK). */
object Harness {
    var passed = 0
    var failed = 0
    val failures = ArrayList<String>()
    private var current = "?"

    var verbose = false

    fun group(name: String) {
        current = name
        println("\n=== $name ===")
    }

    fun check(cond: Boolean, msg: String) {
        if (cond) {
            passed++
            if (verbose) println("PASS [$current] $msg")
        } else {
            failed++
            val line = "FAIL [$current] $msg"
            failures.add(line)
            println(line)
        }
    }

    fun eq(a: Long, b: Long, msg: String) = check(a == b, "$msg (expected $b got $a)")
    fun eq(a: String, b: String, msg: String) = check(a == b, "$msg (expected [$b] got [$a])")
    fun eq(a: BigInteger, b: BigInteger, msg: String) = check(a == b, "$msg (expected $b got $a)")
    fun close(a: Double, b: Double, msg: String) =
        check(abs(a - b) < 1e-9, "$msg (expected $b got $a)")

    fun summary(): Int {
        println("\n----------------------------------------")
        println("TOTAL  passed=$passed  failed=$failed")
        return if (failed == 0) 0 else 1
    }

    /** runs the closure and captures stdout to a file */
    fun runToReport(reportFile: File, block: () -> Unit) {
        val old = System.out
        val fos = java.io.FileOutputStream(reportFile)
        val tee = TeeStream(old, fos)
        System.setOut(java.io.PrintStream(tee))
        try {
            block()
        } finally {
            System.out.flush()
            System.setOut(old)
            fos.close()
        }
    }

    class TeeStream(val a: java.io.PrintStream, val b: java.io.FileOutputStream) : java.io.OutputStream() {
        override fun write(x: Int) {
            a.write(x); b.write(x)
        }
        override fun write(buf: ByteArray, off: Int, len: Int) {
            a.write(buf, off, len); b.write(buf, off, len)
        }
    }
}
