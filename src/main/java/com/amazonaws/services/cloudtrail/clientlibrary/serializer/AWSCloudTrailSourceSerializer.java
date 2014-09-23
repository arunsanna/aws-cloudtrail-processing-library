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
package com.amazonaws.services.cloudtrail.clientlibrary.serializer;

import java.io.IOException;

import com.amazonaws.services.cloudtrail.clientlibrary.model.CloudTrailSource;
import com.amazonaws.services.sqs.model.Message;
import com.fasterxml.jackson.core.JsonParseException;

public interface AWSCloudTrailSourceSerializer {
    /**
     * Get CloudTrailSource from SQS message object.
     *
     * @return
     * @throws MessageParsingException
     * @throws IOException
     * @throws JsonParseException
     */
    public CloudTrailSource getSource(Message sqsMessage) throws IOException;
}
