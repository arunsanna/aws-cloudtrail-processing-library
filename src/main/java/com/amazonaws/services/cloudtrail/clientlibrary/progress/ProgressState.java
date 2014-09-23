/*******************************************************************************
 * Copyright (c) 2014 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Amazon Software License (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://aws.amazon.com/asl/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *******************************************************************************/
package com.amazonaws.services.cloudtrail.clientlibrary.progress;

/**
 * CloudTrail progress state.
 */
public enum ProgressState {
    /**
     * Report progress when poll messages from SQS queue
     */
    pollQueue,

    /**
     * Report progress when delete a message from SQS queue.
     */
    deleteMessage,

    /**
     * Report progress when delete a filtered out message from SQS queue.
     */
    deleteFilteredMessage,

    /**
     * Report progress when parse a message from SQS queue.
     */
    parseMessage,

    /**
     * Report progress when process source
     */
    processSource,

    /**
     * Report progress when download log file
     */
    downloadLog,

    /**
     * Report progress when process log file
     */
    processLog,

    /**
     * Report progress when uncaught exception happened
     */
    uncaughtException
}
