/**
 * hub-common
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
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
 */
package com.blackducksoftware.integration.hub.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.hub.api.generated.component.ProjectRequest;
import com.blackducksoftware.integration.hub.api.generated.discovery.ApiDiscovery;
import com.blackducksoftware.integration.hub.api.generated.response.CurrentVersionView;
import com.blackducksoftware.integration.hub.api.generated.view.CodeLocationView;
import com.blackducksoftware.integration.hub.api.view.ScanSummaryView;
import com.blackducksoftware.integration.hub.cli.CLIDownloadUtility;
import com.blackducksoftware.integration.hub.cli.CLILocation;
import com.blackducksoftware.integration.hub.cli.parallel.ParallelSimpleScanner;
import com.blackducksoftware.integration.hub.cli.summary.Result;
import com.blackducksoftware.integration.hub.cli.summary.ScanServiceOutput;
import com.blackducksoftware.integration.hub.cli.summary.ScanTargetOutput;
import com.blackducksoftware.integration.hub.configuration.HubScanConfig;
import com.blackducksoftware.integration.hub.configuration.HubServerConfig;
import com.blackducksoftware.integration.hub.service.model.ProjectVersionWrapper;
import com.blackducksoftware.integration.util.IntEnvironmentVariables;

public class SignatureScannerService extends DataService {
    private final IntEnvironmentVariables intEnvironmentVariables;
    private final CLIDownloadUtility cliDownloadService;
    private final ProjectService projectDataService;
    private final CodeLocationService codeLocationService;

    private ProjectVersionWrapper projectVersionWrapper;

    public SignatureScannerService(final HubService hubService, final IntEnvironmentVariables intEnvironmentVariables, final CLIDownloadUtility cliDownloadService, final ProjectService projectDataService,
            final CodeLocationService codeLocationService) {
        super(hubService);
        this.intEnvironmentVariables = intEnvironmentVariables;
        this.cliDownloadService = cliDownloadService;
        this.projectDataService = projectDataService;
        this.codeLocationService = codeLocationService;
    }

    public ScanServiceOutput executeScans(final HubServerConfig hubServerConfig, final HubScanConfig hubScanConfig, final ProjectRequest projectRequest)
            throws InterruptedException, IntegrationException {
        return executeScans(hubServerConfig, hubScanConfig, projectRequest, null, 1);
    }

    public ScanServiceOutput executeScans(final HubServerConfig hubServerConfig, final HubScanConfig hubScanConfig, final ProjectRequest projectRequest, final int numberOfParallelProcessors)
            throws InterruptedException, IntegrationException {
        return executeScans(hubServerConfig, hubScanConfig, projectRequest, null, numberOfParallelProcessors);
    }

    public ScanServiceOutput executeScans(final HubServerConfig hubServerConfig, final HubScanConfig hubScanConfig, final ProjectRequest projectRequest, final File signatureScanDirectory, final int numberOfParallelProcessors)
            throws InterruptedException, IntegrationException {
        final CLILocation cliLocation = preScan(hubServerConfig, hubScanConfig, projectRequest, signatureScanDirectory);

        final ParallelSimpleScanner parallelSimpleScanner = new ParallelSimpleScanner(logger, intEnvironmentVariables, hubService.getGson());
        final List<ScanTargetOutput> scanTargetOutputs = parallelSimpleScanner.executeScans(hubServerConfig, hubScanConfig, projectRequest, cliLocation, numberOfParallelProcessors);
        
        logger.info("Starting the post scan steps");
        final List<ScanSummaryView> scanSummaryViews = new ArrayList<>();
        final List<File> standardOutputFiles = new ArrayList<>();
        final List<File> cliLogDirectories = new ArrayList<>();
        for (final ScanTargetOutput scanTargetOutput : scanTargetOutputs) {
            if (scanTargetOutput.getResult() == Result.SUCCESS) {
                scanSummaryViews.add(scanTargetOutput.getScanSummaryView());
                standardOutputFiles.add(scanTargetOutput.getStandardOutputFile());
                cliLogDirectories.add(scanTargetOutput.getCliLogDirectory());
            }
        }
        postScan(hubScanConfig.isCleanupLogsOnSuccess(), standardOutputFiles, cliLogDirectories, hubScanConfig.isDryRun(), scanSummaryViews);
        logger.info("Completed the post scan steps");
        return new ScanServiceOutput(projectVersionWrapper, scanTargetOutputs);
    }

    private CLILocation preScan(final HubServerConfig hubServerConfig, final HubScanConfig hubScanConfig, final ProjectRequest projectRequest, final File signatureScanDirectory) throws IntegrationException {
        printConfiguration(hubScanConfig, projectRequest);
        final CurrentVersionView currentVersion = hubService.getResponse(ApiDiscovery.CURRENT_VERSION_LINK_RESPONSE);
        final File directoryToInstallTo;
        if (null != signatureScanDirectory) {
            directoryToInstallTo = signatureScanDirectory;
        } else {
            directoryToInstallTo = hubScanConfig.getToolsDir();
        }
        final CLILocation cliLocation = cliDownloadService.performInstallation(directoryToInstallTo, hubServerConfig.getHubUrl().toString(), currentVersion.version);

        if (!hubScanConfig.isDryRun()) {
            projectVersionWrapper = projectDataService.getProjectVersionAndCreateIfNeeded(projectRequest);
        }
        return cliLocation;
    }

    private void printConfiguration(final HubScanConfig hubScanConfig, final ProjectRequest projectRequest) {
        logger.alwaysLog(String.format("--> Log Level : %s", logger.getLogLevel().name()));
        String projectName = null;
        String projectVersionName = null;
        String projectVersionPhase = null;
        String projectVersionDistribution = null;
        if (projectRequest != null) {
            projectName = projectRequest.name;
            if (projectRequest.versionRequest != null) {
                projectVersionName = projectRequest.versionRequest.versionName;
                projectVersionPhase = projectRequest.versionRequest.phase == null ? null : projectRequest.versionRequest.phase.toString();
                projectVersionDistribution = projectRequest.versionRequest.distribution == null ? null : projectRequest.versionRequest.distribution.toString();
            }
        }
        logger.alwaysLog(String.format("--> Using Hub Project Name : %s, Version : %s, Phase : %s, Distribution : %s", projectName, projectVersionName, projectVersionPhase, projectVersionDistribution));
        hubScanConfig.print(logger);
    }

    private void postScan(final boolean cleanupLogDirectories, final List<File> standardOutputFiles, final List<File> cliLogDirectories, final boolean dryRun, final List<ScanSummaryView> scanSummaryViews)
            throws IntegrationException {
        if (cleanupLogDirectories) {
            if (!standardOutputFiles.isEmpty()) {
                for (final File standardOutputFile : standardOutputFiles) {
                    if (null != standardOutputFile && standardOutputFile.exists()) {
                        standardOutputFile.delete();
                    }
                }
            }
            if (!cliLogDirectories.isEmpty()) {
                for (final File cliLogDirectory : cliLogDirectories) {
                    if (null != cliLogDirectory && cliLogDirectory.isDirectory()) {
                        for (final File log : cliLogDirectory.listFiles()) {
                            log.delete();
                        }
                        cliLogDirectory.delete();
                    }
                }
            }
        }
        logger.trace(String.format("Scan is dry run %s", dryRun));
        if (!dryRun) {
            for (final ScanSummaryView scanSummaryView : scanSummaryViews) {
                // TODO update when ScanSummaryView is part of the swagger
                final String codeLocationUrl = hubService.getFirstLinkSafely(scanSummaryView, ScanSummaryView.CODELOCATION_LINK);

                final CodeLocationView codeLocationView = hubService.getResponse(codeLocationUrl, CodeLocationView.class);
                codeLocationService.mapCodeLocation(codeLocationView, projectVersionWrapper.getProjectVersionView());
            }
        }
    }
}
