/**
 * Main entry point for SAVE CLI execution
 */

package com.akuleshov7.cli

import com.akuleshov7.cli.parser.readConfigFromArgs

fun main(args: Array<String>) {
    val config = readConfigFromArgs(args)
}
