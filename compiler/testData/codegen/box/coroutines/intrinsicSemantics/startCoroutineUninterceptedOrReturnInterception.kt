// WITH_STDLIB
// WITH_COROUTINES
// IGNORE_BACKEND: NATIVE
import helpers.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.test.assertEquals

var callback: () -> Unit = {}

suspend fun suspendHere(): String = suspendCoroutine { x ->
    callback = {
        x.resume("OK")
    }
}

suspend fun suspendWithException(): String = suspendCoroutine { x ->
    callback = {
        x.resumeWithException(RuntimeException("OK"))
    }
}

fun builder(testNum: Int, shouldSuspend: Boolean, expectedCount: Int, c: suspend () -> String): String {
    callback = {}
    var fromSuspension: String? = null
    var counter = 0

    val result = try {
        c.startCoroutineUninterceptedOrReturn(object: ContinuationAdapter<String>() {
            override val context: CoroutineContext
                get() =  ContinuationDispatcher { counter++ }

            override fun resumeWithException(exception: Throwable) {
                fromSuspension = "Exception: " + exception.message!!
            }

            override fun resume(value: String) {
                fromSuspension = value
            }
        })
    } catch (e: Exception) {
        "Exception: ${e.message}"
    }

    callback()

    if (counter != expectedCount) throw RuntimeException("fail 0 $testNum $counter != $expectedCount")

    if (shouldSuspend) {
        if (result !== COROUTINE_SUSPENDED) throw RuntimeException("fail 1 $testNum $result")
        if (fromSuspension == null) throw RuntimeException("fail 2 $testNum")
        return fromSuspension!!
    }

    if (result === COROUTINE_SUSPENDED) throw RuntimeException("fail 3 $testNum")
    return result as String
}

class ContinuationDispatcher(val dispatcher: () -> Unit) : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> = DispatchedContinuation(dispatcher, continuation)
}

private class DispatchedContinuation<T>(
        val dispatcher: () -> Unit,
        val continuation: Continuation<T>
): ContinuationAdapter<T>() {
    override val context: CoroutineContext = continuation.context

    override fun resume(value: T) {
        dispatcher()
        continuation.resume(value)
    }

    override fun resumeWithException(exception: Throwable) {
        dispatcher()
        continuation.resumeWithException(exception)
    }
}

fun box(): String {
    if (builder(0, false, 0) { "OK" } != "OK") return "fail 4"
    if (builder(1, true, 1) { suspendHere() } != "OK") return "fail 5"
    if (builder(2, true, 1) { suspend{}(); suspendHere() } != "OK") return "fail 51"

    if (builder(3, false, 0) { throw RuntimeException("OK") } != "Exception: OK") return "fail 6"
    if (builder(4, true, 1) { suspendWithException() } != "Exception: OK") return "fail 7"
    if (builder(5, true, 1) { suspend{}(); suspendWithException() } != "Exception: OK") return "fail 71"

    if (builder(6, true, 1, ::suspendHere) != "OK") return "fail 8"
    if (builder(7, true, 1, ::suspendWithException) != "Exception: OK") return "fail 9"

    return "OK"
}
