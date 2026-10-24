package io.github.teslablecontrol.protocol

import com.tesla.generated.signatures.Signatures
import com.tesla.generated.universalmessage.UniversalMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Classification follows `pkg/protocol/error.go`. */
class ErrorsTest {
    private fun reply(fault: UniversalMessage.MessageFault_E? = null, status: UniversalMessage.OperationStatus_E? = null) =
        UniversalMessage.RoutableMessage.newBuilder().apply {
            if (fault != null || status != null) {
                setSignedMessageStatus(
                    UniversalMessage.MessageStatus.newBuilder().apply {
                        fault?.let { setSignedMessageFault(it) }
                        status?.let { setOperationStatus(it) }
                    },
                )
            }
        }.build()

    @Test
    fun successHasNoError() {
        assertNull(reply().protocolError())
        assertNull(reply(status = UniversalMessage.OperationStatus_E.OPERATIONSTATUS_ERROR).protocolError())
    }

    @Test
    fun unknownKeyMeansNotPaired() {
        val error = reply(fault = UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_UNKNOWN_KEY_ID).protocolError()
        assertEquals(ErrorKind.KEY_NOT_PAIRED, (error as CommandException).kind)
        assertFalse(error.shouldRetry())
    }

    @Test
    fun retriableFaults() {
        for (fault in listOf(
            UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_BUSY,
            UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INCORRECT_EPOCH,
            UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INVALID_TOKEN_OR_COUNTER,
            UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_TIME_EXPIRED,
        )) {
            assertTrue(reply(fault = fault).protocolError()!!.shouldRetry(), fault.name)
        }
        val denied = reply(fault = UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INSUFFICIENT_PRIVILEGES).protocolError()!!
        assertFalse(denied.shouldRetry())
        val mtu = reply(fault = UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_RESPONSE_MTU_EXCEEDED).protocolError()!!
        assertTrue(mtu.mayHaveSucceeded)
    }

    @Test
    fun waitMeansBusy() {
        val error = reply(status = UniversalMessage.OperationStatus_E.OPERATIONSTATUS_WAIT).protocolError() as CommandException
        assertEquals(ErrorKind.BUSY, error.kind)
        assertTrue(error.shouldRetry())
    }

    @Test
    fun sessionInfoNotOnWhitelist() {
        val info = Signatures.SessionInfo.newBuilder()
            .setStatus(Signatures.Session_Info_Status.SESSION_INFO_STATUS_KEY_NOT_ON_WHITELIST).build()
        val message = UniversalMessage.RoutableMessage.newBuilder().setSessionInfo(info.toByteString()).build()
        assertEquals(ErrorKind.KEY_NOT_PAIRED, (message.protocolError() as CommandException).kind)
    }

    @Test
    fun timeoutsMayHaveSucceededAndAreNotRetried() {
        val timeout = VehicleErrors.responseTimeout()
        assertTrue(timeout.mayHaveSucceeded)
        assertFalse(timeout.shouldRetry())
    }
}
