package com.synopsys.integration.blackduck.api.recipe;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.synopsys.integration.blackduck.api.generated.view.CodeLocationView;
import com.synopsys.integration.blackduck.api.generated.view.ProjectVersionView;
import com.synopsys.integration.blackduck.service.CodeLocationService;
import com.synopsys.integration.blackduck.service.HubService;
import com.synopsys.integration.blackduck.service.ProjectService;
import com.synopsys.integration.blackduck.service.model.ProjectVersionWrapper;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.test.annotation.IntegrationTest;

@Category(IntegrationTest.class)
public class BdioUploadRecipeTest extends BasicRecipe {
    private final String codeLocationName = "hub_common_27_0_0_SNAPSHOT_upload_recipe";
    private final String uniqueProjectName = "hub-common_with_project_in_bdio";
    private ProjectVersionWrapper projectVersionWrapper;

    @After
    public void cleanup() {
        if (projectVersionWrapper != null) {
            deleteProject(projectVersionWrapper.getProjectView().name);
        }
        deleteCodeLocation(codeLocationName);
    }

    @Test
    public void testBdioUpload() throws IntegrationException {
        final File file = restConnectionTestHelper.getFile("bdio/hub_common_bdio_with_project_section.jsonld");
        /**
         * in this case we can upload the bdio and it will be mapped to a project and version because it has the Project information within the bdio file
         */
        hubServicesFactory.createCodeLocationService().importBomFile(file);
        // TODO remove when we have a notification for bom calc complete
        try {
            Thread.sleep(30 * 1000);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        final ProjectService projectService = hubServicesFactory.createProjectService();
        projectVersionWrapper = projectService.getProjectVersion(uniqueProjectName, "27.0.0-SNAPSHOT");
        final HubService hubService = hubServicesFactory.createHubService();
        final List<CodeLocationView> versionCodeLocations = hubService.getAllResponses(projectVersionWrapper.getProjectVersionView(), ProjectVersionView.CODELOCATIONS_LINK_RESPONSE);
        assertEquals(1, versionCodeLocations.size());
        final CodeLocationView versionCodeLocation = versionCodeLocations.get(0);
        assertEquals(codeLocationName, versionCodeLocation.name);
    }

    @Test
    public void testBdioUploadAndMapToVersion() throws IntegrationException {
        final File file = restConnectionTestHelper.getFile("bdio/hub_common_bdio_without_project_section.jsonld");
        /**
         * in this case we upload the bdio but we have to map it to a project and version ourselves since the Project information is missing in the bdio file
         */
        hubServicesFactory.createCodeLocationService().importBomFile(file);

        /**
         * now that the file is uploaded, we want to lookup the code location that was created by the upload. in this case we know the name of the code location that was specified in the bdio file
         */
        final CodeLocationService codeLocationService = hubServicesFactory.createCodeLocationService();
        final CodeLocationView codeLocationView = codeLocationService.getCodeLocationByName(codeLocationName);

        /**
         * then we map the code location to a version
         */
        final ProjectService projectService = hubServicesFactory.createProjectService();
        projectVersionWrapper = projectService.getProjectVersionAndCreateIfNeeded(uniqueProjectName, "27.0.0-SNAPSHOT");

        codeLocationService.mapCodeLocation(codeLocationView, projectVersionWrapper.getProjectVersionView());

        final HubService hubService = hubServicesFactory.createHubService();
        final List<CodeLocationView> versionCodeLocations = hubService.getAllResponses(projectVersionWrapper.getProjectVersionView(), ProjectVersionView.CODELOCATIONS_LINK_RESPONSE);
        final CodeLocationView versionCodeLocation = versionCodeLocations.get(0);
        assertEquals(codeLocationName, versionCodeLocation.name);
    }
}
