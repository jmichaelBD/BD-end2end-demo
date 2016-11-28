/*******************************************************************************
 * Copyright (C) 2016 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package com.blackducksoftware.integration.hub.api;

import static com.blackducksoftware.integration.hub.api.UrlConstants.SEGMENT_API;
import static com.blackducksoftware.integration.hub.api.UrlConstants.SEGMENT_REGISTRATIONS;
import static com.blackducksoftware.integration.hub.api.UrlConstants.SEGMENT_V1;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import org.restlet.data.Method;

import com.blackducksoftware.integration.hub.exception.BDRestException;
import com.blackducksoftware.integration.hub.rest.RestConnection;
import com.google.gson.JsonObject;

public class HubRegistrationRestService extends HubRestService {
    private static final List<String> REGISTRATION_SEGMENTS = Arrays.asList(SEGMENT_API, SEGMENT_V1, SEGMENT_REGISTRATIONS);

    public HubRegistrationRestService(RestConnection restConnection) {
        super(restConnection);
    }

    public String getRegistrationId() throws IOException, URISyntaxException, BDRestException {
        final HubPagedRequest registrationRequest = new HubPagedRequest(getRestConnection());
        registrationRequest.setMethod(Method.GET);
        registrationRequest.addUrlSegments(REGISTRATION_SEGMENTS);

        final JsonObject jsonObject = registrationRequest.executeForResponseJson();
        String registrationId = jsonObject.get("registrationId").getAsString();
        return registrationId;
    }

}