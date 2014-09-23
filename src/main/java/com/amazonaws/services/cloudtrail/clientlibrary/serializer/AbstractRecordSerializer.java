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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.services.cloudtrail.clientlibrary.model.CloudTrailClientRecord;
import com.amazonaws.services.cloudtrail.clientlibrary.model.CloudTrailDeliveryInfo;
import com.amazonaws.services.cloudtrail.clientlibrary.model.internal.CloudTrailRecord;
import com.amazonaws.services.cloudtrail.clientlibrary.model.internal.CloudTrailRecordField;
import com.amazonaws.services.cloudtrail.clientlibrary.model.internal.Resource;
import com.amazonaws.services.cloudtrail.clientlibrary.model.internal.SessionContext;
import com.amazonaws.services.cloudtrail.clientlibrary.model.internal.SessionIssuer;
import com.amazonaws.services.cloudtrail.clientlibrary.model.internal.UserIdentity;
import com.amazonaws.services.cloudtrail.clientlibrary.model.internal.WebIdentitySessionContext;
import com.amazonaws.services.cloudtrail.clientlibrary.utils.ClientLibraryUtils;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;

public abstract class AbstractRecordSerializer implements AWSCloudTrailRecordSerializer{
    private static final Log logger = LogFactory.getLog(AbstractRecordSerializer.class);

    public abstract CloudTrailDeliveryInfo getDeliveryInfo(int charStart, int charEnd);

    private static final String RECORDS = "Records";

    private static final double SUPPORTED_EVENT_VERSION = 1.02d;

    /**
     * Jackson JSON Parser
     */
    private JsonParser jsonParser;

    /**
     * Construct an instance of RecordSerializer object
     *
     * @param inputBytes
     * @param s3ObjectKey
     * @param s3BucketName
     * @throws IOException
     */
    public AbstractRecordSerializer (JsonParser jsonParser) throws IOException {
        this.jsonParser = jsonParser;
    }

    /**
     * Read off header part of AWS CloudTrail log.
     * @throws JsonParseException
     * @throws IOException
     */
    protected void readArrayHeader() throws JsonParseException, IOException {
        if (this.jsonParser.nextToken() != JsonToken.START_OBJECT) {
            throw new JsonParseException("Not a Json object", this.jsonParser.getCurrentLocation());
        }

        this.jsonParser.nextToken();
        if (!jsonParser.getText().equals(RECORDS)) {
            throw new JsonParseException("Not a CloudTrail log", this.jsonParser.getCurrentLocation());
        }

        if (this.jsonParser.nextToken() != JsonToken.START_ARRAY) {
            throw new JsonParseException("Not a CloudTrail log", this.jsonParser.getCurrentLocation());
        }
    }

    /**
     * In Fasterxml parser, hasNextRecord will consume next token. So do not call it multiple times.
     */
    public boolean hasNextRecord() throws IOException {
        JsonToken nextToken = this.jsonParser.nextToken();
        return nextToken == JsonToken.START_OBJECT || nextToken == JsonToken.START_ARRAY;
    }

    /**
     * Close underlining jsonReader object, call it upon processed a log file
     *
     * @throws IOException
     */
    public void close() throws IOException {
        this.jsonParser.close();
    }

    /**
     * Get next AWSCloudTrailClientRecord record from log file. When failed to parse a record,
     * AWSCloudTrailClientParsingException will be thrown. In this case, the charEnd index
     * the place we encountered parsing error.
     * @throws IOException
     * @throws JsonParseException
     */
    public CloudTrailClientRecord getNextRecord() throws IOException {
        CloudTrailRecord record = new CloudTrailRecord();
        String key = null;

        // record's first character position in the log file.
        int charStart = (int) this.jsonParser.getCurrentLocation().getCharOffset();

        while(this.jsonParser.nextToken() != JsonToken.END_OBJECT) {
            key = jsonParser.getCurrentName();

            switch (key) {
            case "eventVersion":
                String eventVersion = this.jsonParser.nextTextValue();
                if (Double.parseDouble(eventVersion) > SUPPORTED_EVENT_VERSION) {
                    logger.warn(String.format("EventVersion %s is not supported by CloudTrail Java Client.", eventVersion));
                }
                record.add(key, eventVersion);
                break;
            case "userIdentity":
                this.parseUserIdentity(record);
                break;
            case "eventTime":
                record.add(CloudTrailRecordField.eventTime.name(), this.convertToDate(this.jsonParser.nextTextValue()));
                break;
            case "eventID":
            case "requestID":
                record.add(key, this.convertToUUID(this.jsonParser.nextTextValue()));
                break;
            case "readOnly":
                this.parseReadOnly(record);
                break;
            case "resources":
                this.parseResources(record);
                break;
            default:
                record.add(key, this.parseDefaultValue(key));
                break;
            }
        }
        this.setAccountId(record);

        // record's last character position in the log file.
        int charEnd = (int) this.jsonParser.getCurrentLocation().getCharOffset();

        CloudTrailDeliveryInfo deliveryInfo = this.getDeliveryInfo(charStart, charEnd);

        return new CloudTrailClientRecord(record, deliveryInfo);
    }

    /**
     * Set AccountId in AWSCloudTrailRecord top level from either UserIdentity Top level or from
     * SessionIssuer. The AccountId in UserIdentity has higher precedence than AccountId in
     * SessionIssuer (if exists).
     *
     * @param record
     */
    private void setAccountId(CloudTrailRecord record) {
        if (record.getUserIdentity() == null) {
            return;
        }

        if (record.getUserIdentity().getAccountId() != null) {
            record.add("accountId", record.getUserIdentity().getAccountId());
        } else {
            SessionContext sessionContext = record.getUserIdentity().getSessionContext();
            if (sessionContext != null && sessionContext.getSessionIssuer() != null) {
                record.add("accountId", sessionContext.getSessionIssuer().getAccountId());
            }
        }
    }

    /**
     * Parse user identity in AWSCloudTrailRecord
     *
     * @param record
     * @throws IOException
     */
    private void parseUserIdentity(CloudTrailRecord record) throws IOException {
        JsonToken nextToken = this.jsonParser.nextToken();
        if (nextToken == JsonToken.VALUE_NULL) {
            record.add(CloudTrailRecordField.userIdentity.name(), null);
            return;
        }

        if (nextToken != JsonToken.START_OBJECT) {
            throw new JsonParseException("Not a UserIdentity object", this.jsonParser.getCurrentLocation());
        }

        UserIdentity userIdentity = new UserIdentity();

        while (this.jsonParser.nextToken() != JsonToken.END_OBJECT) {
            String key = this.jsonParser.getCurrentName();

            switch (key) {
            case "type":
                userIdentity.add(CloudTrailRecordField.type.name(), this.jsonParser.nextTextValue());
                break;
            case "principalId":
                userIdentity.add(CloudTrailRecordField.principalId.name(), this.jsonParser.nextTextValue());
                break;
            case "arn":
                userIdentity.add(CloudTrailRecordField.arn.name(), this.jsonParser.nextTextValue());
                break;
            case "accountId":
                userIdentity.add(CloudTrailRecordField.accountId.name(), this.jsonParser.nextTextValue());
                break;
            case "accessKeyId":
                userIdentity.add(CloudTrailRecordField.accessKeyId.name(), this.jsonParser.nextTextValue());
                break;
            case "userName":
                userIdentity.add(CloudTrailRecordField.userName.name(), this.jsonParser.nextTextValue());
                break;
            case "sessionContext":
                this.parseSessionContext(userIdentity);
                break;
            case "invokedBy":
                userIdentity.add(CloudTrailRecordField.invokedBy.name(), this.jsonParser.nextTextValue());
                break;
            default:
                userIdentity.add(key, this.parseDefaultValue(key));
                break;
            }
        }
        record.add(CloudTrailRecordField.userIdentity.name(), userIdentity);
    }

    /**
     * Parse session context object
     *
     * @return
     * @throws IOException
     */
    private void parseSessionContext(UserIdentity userIdentity) throws IOException {
        if (this.jsonParser.nextToken() != JsonToken.START_OBJECT) {
            throw new JsonParseException("Not a SessionContext object", this.jsonParser.getCurrentLocation());
        }

        SessionContext sessionContext = new SessionContext();

        while (this.jsonParser.nextToken() != JsonToken.END_OBJECT) {
            String key = this.jsonParser.getCurrentName();

            switch (key) {
            case "attributes":
                sessionContext.add(CloudTrailRecordField.attributes.name(), this.parseAttributes());
                break;
            case "sessionIssuer":
                sessionContext.add(CloudTrailRecordField.sessionIssuer.name(), this.parseSessionIssuer(sessionContext));
                break;
            case "webIdFederationData":
                sessionContext.add(CloudTrailRecordField.webIdFederationData.name(), this.parseWebIdentitySessionContext(sessionContext));
                break;
            default:
                sessionContext.add(key, this.parseDefaultValue(key));
                break;
            }
        }

        userIdentity.add(CloudTrailRecordField.sessionContext.name(), sessionContext);

    }

    /**
     * Parse web identify session object
     *
     * @return
     * @throws IOException
     */
    private WebIdentitySessionContext parseWebIdentitySessionContext(SessionContext sessionContext) throws IOException {
        if (this.jsonParser.nextToken() != JsonToken.START_OBJECT) {
            throw new JsonParseException("Not a WebIdentitySessionContext object", this.jsonParser.getCurrentLocation());
        }

        WebIdentitySessionContext webIdFederationData = new WebIdentitySessionContext();

        while (this.jsonParser.nextToken() != JsonToken.END_OBJECT) {
            String key = this.jsonParser.getCurrentName();

            switch (key) {
            case "attributes":
                webIdFederationData.add(CloudTrailRecordField.attributes.name(), this.parseAttributes());
                break;
            case "federatedProvider":
                webIdFederationData.add(CloudTrailRecordField.federatedProvider.name(), this.jsonParser.nextTextValue());
                break;
            default:
                webIdFederationData.add(key, this.parseDefaultValue(key));
                break;
            }
        }

        return webIdFederationData;
    }


    /**
     * Parse session issuer object. It only happened on role session and federated session.
     *
     * @return
     * @throws IOException
     */
    private SessionIssuer parseSessionIssuer(SessionContext sessionContext) throws IOException {
        if (this.jsonParser.nextToken() != JsonToken.START_OBJECT) {
            throw new JsonParseException("Not a SessionIssuer object", this.jsonParser.getCurrentLocation());
        }

        SessionIssuer sessionIssuer = new SessionIssuer();

        while (this.jsonParser.nextToken() != JsonToken.END_OBJECT) {
            String key = this.jsonParser.getCurrentName();

            switch (key) {
            case "type":
                sessionIssuer.add(CloudTrailRecordField.type.name(), this.jsonParser.nextTextValue());
                break;
            case "principalId":
                sessionIssuer.add(CloudTrailRecordField.principalId.name(), this.jsonParser.nextTextValue());
                break;
            case "arn":
                sessionIssuer.add(CloudTrailRecordField.arn.name(), this.jsonParser.nextTextValue());
                break;
            case "accountId":
                sessionIssuer.add(CloudTrailRecordField.accountId.name(), this.jsonParser.nextTextValue());
                break;
            case "userName":
                sessionIssuer.add(CloudTrailRecordField.userName.name(), this.jsonParser.nextTextValue());
                break;
            default:
                sessionIssuer.add(key, this.parseDefaultValue(key));
                break;
            }
        }

        return sessionIssuer;
    }

    /**
     * Parse record read only attribute.
     *
     * @param record
     * @throws JsonParseException
     * @throws IOException
     */
    private void parseReadOnly(CloudTrailRecord record) throws JsonParseException, IOException {
        this.jsonParser.nextToken();
        Boolean readOnly = null;
        if (this.jsonParser.getCurrentToken() != JsonToken.VALUE_NULL) {
            readOnly = this.jsonParser.getBooleanValue();
        }
        record.add(CloudTrailRecordField.readOnly.name(), readOnly);
    }

    /**
     * Parse a list of Resource
     * @param record the resources belong to
     * @throws IOException
     */
    private void parseResources(CloudTrailRecord record) throws IOException {
        JsonToken nextToken = this.jsonParser.nextToken();
        if (nextToken == JsonToken.VALUE_NULL) {
            record.add(CloudTrailRecordField.resources.name(), null);
            return;
        }

        if (nextToken != JsonToken.START_ARRAY) {
            throw new JsonParseException("Not a list of resources object", this.jsonParser.getCurrentLocation());
        }

        List<Resource> resources = new ArrayList<Resource>();

        while (this.jsonParser.nextToken() != JsonToken.END_ARRAY) {
            resources.add(this.parseResource());
        }

        record.add(CloudTrailRecordField.resources.name(), resources);
    }

    /**
     * Parse a single Resource
     *
     * @return a single resource
     * @throws IOException
     */
    private Resource parseResource() throws IOException {
        //current token is ready consumed by parseResources
        if (this.jsonParser.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new JsonParseException("Not a Resource object", this.jsonParser.getCurrentLocation());
        }

        Resource resource = new Resource();

        while (this.jsonParser.nextToken() != JsonToken.END_OBJECT) {
            String key = this.jsonParser.getCurrentName();

            switch (key) {
            default:
                resource.add(key, this.parseDefaultValue(key));
                break;
            }
        }

        return resource;
    }

    /**
     * Parse the record with key as default value.
     *
     * If the value is JSON null, then we will return null.
     * If the value is JSON object (of starting with START_ARRAY or START_OBject) , then we will convert the object to String.
     * If the value is JSON scalar value (non-structured object), then we will return simply return it as String.
     *
     * @param record
     * @param key
     * @throws IOException
     */
    private String parseDefaultValue(String key) throws IOException {
        this.jsonParser.nextToken();
        String value = null;
        JsonToken currentToken = this.jsonParser.getCurrentToken();
        if (currentToken != JsonToken.VALUE_NULL) {
            if (currentToken == JsonToken.START_ARRAY || currentToken == JsonToken.START_OBJECT) {
                JsonNode node = this.jsonParser.readValueAsTree();
                value = node.toString();
            } else {
                value = this.jsonParser.getValueAsString();
            }
        }
        return value;
    }

    /**
     * Parse attributes as a Map, used in both parseWebIdentitySessionContext and parseSessionContext
     *
     * @return attributes for either session context or web identity session context
     * @throws IOException
     */
    private Map<String, String> parseAttributes() throws IOException {
        if (this.jsonParser.nextToken() != JsonToken.START_OBJECT) {
            throw new JsonParseException("Not a Attributes object", this.jsonParser.getCurrentLocation());
        }

        Map<String, String> attributes = new HashMap<>();

        while (this.jsonParser.nextToken() != JsonToken.END_OBJECT) {
            String key = this.jsonParser.getCurrentName();
            String value = this.jsonParser.nextTextValue();
            attributes.put(key, value);
        }

        return attributes;
    }

    /**
     * This method convert a String to UUID type. Currently EventID is in UUID type.
     *
     * @param str that need to convert to UUID
     * @return
     */
    private UUID convertToUUID(String str) {
        return UUID.fromString(str);
    }

    /**
     * This method convert a String to Date type. When parse error happened return current date.
     *
     * @param dateInString the String to convert to Date
     * @return Date the date and time in coordinated universal time
     * @throws IOException
     */
    private Date convertToDate(String dateInString) throws IOException {
        Date date = null;
        if (dateInString != null) {
            try {
                date = ClientLibraryUtils.getUtcSdf().parse(dateInString);
            } catch (ParseException e) {
                throw new IOException("Cannot parse " + dateInString + " as Date", e);
            }
        }
        return date;
    }
}
