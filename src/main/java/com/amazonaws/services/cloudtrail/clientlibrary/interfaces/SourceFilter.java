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
package com.amazonaws.services.cloudtrail.clientlibrary.interfaces;

import com.amazonaws.services.cloudtrail.clientlibrary.exceptions.CallbackException;
import com.amazonaws.services.cloudtrail.clientlibrary.model.CloudTrailSource;

/**
 * SourceFilter is a call back function that hands a CloudTrailSource to user. User can
 * determinate whether want to process this source. The filter() method is invoked after
 * polled SQS message from SQS queue and before process records. For performance, CloudTrailSource
 * is not cloned, caller should not change the content of source.
 */
public interface SourceFilter{

    /**
     * A method filter source.
     *
     * @param source
     * @return true if the source should be processed by AWSCloudTrailClientLibrary.
     * @throws CallbackException when error happened during process CloudTrailClientRecords. AWSCloudTrailClientLibrary
     *         will eventually hand this exception back to ExceptionHandler.
     */
    public boolean filterSource(final CloudTrailSource source) throws CallbackException;

}