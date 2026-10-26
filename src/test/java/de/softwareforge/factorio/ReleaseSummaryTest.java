package de.softwareforge.factorio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseSummaryTest {
    @TempDir Path root;

    private void mod(String options) throws Exception {
        Files.writeString(root.resolve("info.json"), """
            {"name":"example","version":"3.4.3","package":%s}
            """.formatted(options));
    }

    @Test void detailsRouteConfirmsMetadataWithoutInspectingPublishConfiguration() throws Exception {
        mod("""
            {"sync_portal_details":false,"git_publish_branch":"master",
             "readme":"portal/description.md","faq":"portal/questions.md"}
            """);
        Path invalidPublishConfig=root.resolve("config.json");
        Files.writeString(invalidPublishConfig,"not valid JSON");
        var arguments=new FactorioActions.Details().initialArguments();
        assertEquals(List.of("details"),arguments);
        var preview=ReleaseSummary.create(root,arguments,invalidPublishConfig.toString(),root.resolve("no-git").toString());
        assertEquals("Synchronize Mod Portal Details",preview.title());
        assertEquals("Synchronize Details",preview.acceptText());
        assertTrue(preview.message().contains("portal/description.md"));
        assertTrue(preview.message().contains("portal/questions.md"));
        assertTrue(preview.message().contains("title, homepage and summary"));
        assertTrue(preview.message().contains("No release ZIP is uploaded"));
        assertTrue(preview.message().contains("local version and Git repository are unchanged"));
        for(String irrelevant:List.of("Release tag:","Push destination:","Commit author:","Publish performs","sync_portal_details"))
            assertFalse(preview.message().contains(irrelevant),irrelevant);
    }

    @Test void detailsShowsDefaultSourcesAndGalleryRemovalPolicy() throws Exception {
        mod("{}");
        var basic=ReleaseSummary.create(root,List.of("details"),"","").message();
        assertTrue(basic.contains("readme.md"));
        assertTrue(basic.contains("faq.md"));
        assertFalse(basic.contains("reordered"));
        mod("{\"gallery\":[\"screenshots/*.png\"]}");
        var pruning=ReleaseSummary.create(root,List.of("details"),"","").message();
        assertTrue(pruning.contains("screenshots/*.png"));
        assertTrue(pruning.contains("images outside the synchronized set removed"));
        mod("{\"gallery\":[],\"prune_gallery\":false}");
        var retaining=ReleaseSummary.create(root,List.of("details"),"","").message();
        assertTrue(retaining.contains("existing images will be retained"));
        assertFalse(retaining.contains("removed"));
    }

    @Test void uploadRouteConfirmsOnlyTheSelectedArchive() throws Exception {
        mod("{}");
        assertEquals(List.of("upload"),new FactorioActions.Upload().initialArguments());
        var zip=root.resolve("example_3.4.3.zip").toString();
        var preview=ReleaseSummary.create(root,List.of("upload",zip),"",root.resolve("no-git").toString());
        assertEquals("Upload Mod ZIP",preview.title());
        assertEquals("Upload ZIP",preview.acceptText());
        assertTrue(preview.message().contains(zip));
        assertTrue(preview.message().contains("existing archive"));
        assertFalse(preview.message().contains("Publish performs"));
        assertThrows(IllegalArgumentException.class,()->ReleaseSummary.create(root,List.of("upload"),"",""));
    }

    @Test void publishRetainsItsReleaseSideEffectWarning() throws Exception {
        mod("{}");
        var arguments=new FactorioActions.Publish().initialArguments();
        assertEquals(List.of("publish"),arguments);
        var preview=ReleaseSummary.create(root,arguments,"","");
        assertEquals("Publish Mod",preview.title());
        assertEquals("Publish",preview.acceptText());
        assertTrue(preview.message().contains("Release tag: 3.4.3"));
        assertTrue(preview.message().contains("version increment and Git push"));
    }
}
