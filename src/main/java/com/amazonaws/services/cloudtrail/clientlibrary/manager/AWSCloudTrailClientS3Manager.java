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
package com.amazonaws.services.cloudtrail.clientlibrary.manager;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cloudtrail.clientlibrary.configuration.AWSCloudTrailClientConfiguration;
import com.amazonaws.services.cloudtrail.clientlibrary.exceptions.ClientLibraryException;
import com.amazonaws.services.cloudtrail.clientlibrary.interfaces.ExceptionHandler;
import com.amazonaws.services.cloudtrail.clientlibrary.interfaces.ProgressReporter;
import com.amazonaws.services.cloudtrail.clientlibrary.model.CloudTrailLog;
import com.amazonaws.services.cloudtrail.clientlibrary.model.CloudTrailSource;
import com.amazonaws.services.cloudtrail.clientlibrary.progress.BasicProcessLogInfo;
import com.amazonaws.services.cloudtrail.clientlibrary.progress.ProgressState;
import com.amazonaws.services.cloudtrail.clientlibrary.progress.ProgressStatus;
import com.amazonaws.services.cloudtrail.clientlibrary.utils.ClientLibraryUtils;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

/**
 * A convenient class to manage Amazon S3 Service related operations.
 */
public class AWSCloudTrailClientS3Manager {
    private static final Log logger = LogFactory.getLog(AWSCloudTrailClientSqsManager.class);

    /**
     * An instance of AmazonS3Client.
     */
    private AmazonS3Client s3Client;

    /**
     * An instance of AWSCloudTrailClientConfiguration
     */
    private AWSCloudTrailClientConfiguration config;

    /**
     * User implementation of ExceptionHandler, used to handle error case.
     */
    private ExceptionHandler exceptionHandler;

    /**
     * User implementation of ProgressReporter, used to report progress
     */
    private ProgressReporter progressReporter;

    /**
     * S3Manager constructor
     * @param configuration
     */
    public AWSCloudTrailClientS3Manager(AmazonS3Client s3Client, AWSCloudTrailClientConfiguration configuration, ExceptionHandler exceptionHandler, ProgressReporter progressReporter) {
        this.config = configuration;
        this.exceptionHandler = exceptionHandler;
        this.progressReporter = progressReporter;
        this.s3Client = s3Client;
        this.validate();
    }

    /**
     *
     * @param ctLog
     * @param source
     * @return
     */
    public byte[] downloadLog(CloudTrailLog ctLog, CloudTrailSource source) {
        boolean success = false;
        ProgressStatus startStatus = new ProgressStatus(ProgressState.downloadLog, new BasicProcessLogInfo(source, ctLog, success));
        final Object downloadSourceReportObject = this.progressReporter.reportStart(startStatus);

        byte[] s3ObjectBytes = null;

        // start to download CloudTrail log
        try {
            S3Object s3Object = this.getObject(ctLog.getS3Bucket(), ctLog.getS3ObjectKey());
            try (S3ObjectInputStream s3InputStream = s3Object.getObjectContent()){
                s3ObjectBytes = ClientLibraryUtils.toByteArray(s3InputStream);
            }
            ctLog.setLogFileSize(s3Object.getObjectMetadata().getContentLength());
            success = true;
            logger.info("Downloaded log file " + ctLog.getS3ObjectKey() + " from " + ctLog.getS3Bucket());

        } catch (AmazonServiceException | IOException e) {

            ClientLibraryException exception = new ClientLibraryException("Fail to download log file.", e, startStatus);
            this.exceptionHandler.handleException(exception);

        } finally {
            ProgressStatus endStatus = new ProgressStatus(ProgressState.downloadLog, new BasicProcessLogInfo(source, ctLog, success));
            this.progressReporter.reportEnd(endStatus, downloadSourceReportObject);
        }

        return s3ObjectBytes;
    }

    /**
     * API call to Amazon S3 service to download a S3 object
     *
     * @param bucketName
     * @param objectKey
     * @return
     */
    public S3Object getObject(String bucketName, String objectKey) {
        try {
            return this.s3Client.getObject(bucketName, objectKey);
        } catch (AmazonServiceException e) {
            logger.error("Failed to get object " + objectKey + " from s3 bucket " + bucketName);
            throw e;
        }
    }

    /**
     * Convenient function to validate input
     */
    private void validate() {
        ClientLibraryUtils.checkArgumentNotNull(this.config, "configuration is null");
        ClientLibraryUtils.checkArgumentNotNull(this.exceptionHandler, "exception handler is null");
        ClientLibraryUtils.checkArgumentNotNull(this.progressReporter, "progress reporter is null");
        ClientLibraryUtils.checkArgumentNotNull(this.s3Client, "s3 client is null");
    }
}
