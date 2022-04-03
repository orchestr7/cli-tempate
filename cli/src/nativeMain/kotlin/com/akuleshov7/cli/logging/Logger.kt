/**
 * Logging utilities specific for native code.
 */

package com.akuleshov7.cli.logging

import com.akuleshov7.cli.ExitCodes
import kotlin.system.exitProcess

/**
 * Log [message] with level ERROR and exit process with code [exitCode]
 *
 * @param exitCode exit code
 * @param message message to log
 * @return nothing, program terminates in this method
 */
@Deprecated("never use this method in save-core as it can lead to a break of save-cloud application")
fun logErrorAndExit(exitCode: ExitCodes, message: String): Nothing {
    println(message)
    exitProcess(exitCode.code)
}
