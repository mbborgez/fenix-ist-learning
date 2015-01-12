package pt.ist.learning;

/**
 * Created by borgez on 02-01-2015.
 */

import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.core.domain.User;
import org.fenixedu.bennu.core.groups.AnyoneGroup;
import org.fenixedu.bennu.core.groups.DynamicGroup;
import org.fenixedu.bennu.core.security.Authenticate;
import org.fenixedu.bennu.core.util.CoreConfiguration;
import org.fenixedu.bennu.io.domain.GenericFile;
import org.fenixedu.bennu.io.domain.GroupBasedFile;
import org.fenixedu.bennu.io.servlets.FileDownloadServlet;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.cms.domain.*;
import org.fenixedu.cms.domain.component.StaticPost;
import org.fenixedu.commons.StringNormalizer;
import org.fenixedu.commons.i18n.I18N;
import org.fenixedu.commons.i18n.LocalizedString;
import org.fenixedu.learning.domain.degree.DegreeSite;
import org.fenixedu.learning.domain.degree.DegreeSiteListener;
import org.fenixedu.learning.domain.executionCourse.ExecutionCourseListener;
import org.fenixedu.learning.domain.executionCourse.ExecutionCourseSite;
import org.joda.time.DateTime;
import pt.ist.fenix.domain.homepage.HomepageListener;
import pt.ist.fenix.domain.homepage.HomepageSite;
import pt.ist.fenix.domain.unit.DepartmentListener;
import pt.ist.fenix.domain.unit.ResearchUnitListener;
import pt.ist.fenix.domain.unit.UnitSite;
import pt.ist.fenix.domain.unit.components.UnitHomepageComponent;
import pt.ist.fenixframework.Atomic.TxMode;
import pt.ist.fenixframework.FenixFramework;
import pt.ist.fenix.domain.unit.ScientificAreaListener;
import pt.ist.fenix.domain.unit.ScientificCouncilListener;
import pt.ist.fenix.domain.unit.UnitsListener;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipInputStream;

import static org.fenixedu.bennu.core.i18n.BundleUtil.getLocalizedString;
import static org.fenixedu.cms.domain.component.Component.forType;
import static pt.ist.fenixframework.FenixFramework.getDomainObject;

public class ImportSiteStructures extends CustomTask {
    private static final Path DATA_PATH = Paths.get("/home/borgez/Desktop/unit_sites_export.json");
    private static final LocalizedString BANNER_NAME = new LocalizedString(I18N.getLocale(), "Banner");
    private static final LocalizedString TITLE_HOMEPAGE = getLocalizedString("resources.FenixEduLearningResources", "researchUnit.homepage");
    static final String SERVLET_PATH = "/downloadFile/";

    public void runTask() throws Exception {
        installThemes();
        JsonReader jsonReader = new JsonReader(Files.newReader(DATA_PATH.toFile(), Charset.forName("UTF-8")));
        Iterable<List<JsonElement>> chunks =
                Iterables.partition(new JsonParser().parse(jsonReader).getAsJsonArray(), 100);
        int count = 0;
        int total = Iterables.size(chunks);
        taskLog("Processing %s chunks\n", total);
        for (List<JsonElement> chunk : chunks) {
            for (JsonElement el : chunk) {
                JsonObject json = el.getAsJsonObject();
                String type = json.get("type").getAsString();
                if ("DepartmentSite".equals(type)) {
                    Site site = FenixFramework.getDomainObject(json.get("site").getAsString());
                    if (!site.getMenusSet().isEmpty()) {
                        continue;
                    }
                    FenixFramework.atomic(() -> {
                        generateSlugs(site, type);
                        Menu menu = createMenu(site, ExecutionCourseListener.MENU_TITLE, "default");

                        createDefaultContents(site, menu, type);
                        if(site instanceof UnitSite) {
                            Menu topMenu = createMenu(site, new LocalizedString().with(Locale.getDefault(), "Top"), "top");
                            Menu sideMenu = createMenu(site, new LocalizedString().with(Locale.getDefault(), "Side"), "side");
                            if (json.has("sections")) {
                                for (JsonElement ell : json.get("sections").getAsJsonArray()) {
                                    JsonObject section = ell.getAsJsonObject();
                                    LocalizedString sectionName = LocalizedString.fromJson(section.get("name"));
                                    if(isSpecialSection(sectionName, "top")) {
                                        process(site, section, topMenu, null);
                                    } else if (isSpecialSection(sectionName, "side")) {
                                        process(site, section, sideMenu, null);
                                    } else {
                                        process(site, section, menu, null);
                                    }
                                }
                            }
                        } else {
                            process(site, json, menu, null);
                        }
                    });
                    taskLog("Processing %s - %s - exists ? %s\n", count++, json.get("type").getAsString(), site != null);
                }
            }
        }
    }

    private boolean isSpecialSection(LocalizedString sectionName, String specialSectionName) {
        return sectionName.getLocales().stream().map(locale -> sectionName.getContent(locale))
                .filter(content->specialSectionName.toLowerCase().equals(content.toLowerCase())).findAny().isPresent();
    }

    private Menu createMenu(Site site, LocalizedString name, String slug) {
        Menu menu = new Menu(site);
        menu.setName(name);
        menu.setSlug(slug);
        return menu;
    }

    private void createDefaultContents(Site site, Menu menu, String type) {
        User user = Authenticate.getUser();
        if (site instanceof ExecutionCourseSite) {
            ExecutionCourseListener.createDefaultContents(site, menu, user);
        } else if (site instanceof HomepageSite) {
            HomepageListener.createDefaultContents(site, menu, user);
        } else if (site instanceof DegreeSite) {
            DegreeSiteListener.createDefaultContents(site, menu, user);
        } else if(site instanceof UnitSite) {
            if("DepartmentSite".equals(type)) {
                DepartmentListener.createDefaultContents(site, menu, user);
            } else if("ScientificAreaSite".equals(type)) {
                ScientificAreaListener.createDefaultContents(site, menu, user);
            } else if("ScientificCouncilSite".equals(type)) {
                ScientificCouncilListener.createDefaultContents(site, menu, user);
            } else if("ResearchUnitSite".equals(type)) {
                ResearchUnitListener.createDefaultContents(site, menu, user);
            } else {
                UnitsListener.createDefaultContents(site, menu, user);
            }
        }
    }

    private void generateSlugs(Site site, String type) {
        site.setBennu(Bennu.getInstance());

        if (site.getCreatedBy() == null) {
            site.setCreatedBy(Authenticate.getUser());
        }

        if (site.getFolder() == null) {
            site.setFolder(folderForSite(site, type));
        }

        for (Category cat : site.getCategoriesSet()) {
            if (cat.getCreatedBy() == null) {
                cat.setCreatedBy(Authenticate.getUser());
            }
            if(cat.getSlug() == null) {
                cat.setSlug(StringNormalizer.slugify(cat.getName().getContent()));
            }
        }

        if (site.getSlug() == null || site.getExternalId().equals(site.getSlug())) {
            site.setSlug(StringNormalizer.slugify(site.getBaseUrl()));
        }

        site.getPostSet()
                .stream()
                .filter(post -> (post.getExternalId().equals(post.getSlug()) || post.getSlug() == null) && post.getName() != null
                        && post.getName().getContent() != null).forEach(post -> {
            post.setSlug(StringNormalizer.slugify(post.getName().getContent()));
            if (post.getCreatedBy() == null) {
                post.setCreatedBy(Authenticate.getUser());
            }
        });

        try {
            Constructor<?> ctor = PersistentSiteViewersGroup.class.getDeclaredConstructor(Site.class);
            ctor.setAccessible(true);
            ctor.newInstance(site);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (site.getTheme() == null) {
            site.setTheme(themeForSite(site));
        }
    }

    private CMSTheme themeForSite(Site site) {
        if (site instanceof ExecutionCourseSite) {
            return CMSTheme.forType("fenixedu-learning-theme");
        } else if (site instanceof HomepageSite) {
            return CMSTheme.forType("fenixedu-homepages-theme");
        } else if(site instanceof UnitSite) {
            return CMSTheme.forType("fenixedu-units-theme");
        } else {
            return null;
        }
    }

    private CMSFolder folderForSite(Site site, String type) {
        if (site instanceof ExecutionCourseSite) {
            return folder("/disciplinas");
        } else if (site instanceof HomepageSite) {
            return folder("/homepage");
        } else if (site instanceof UnitSite) {
            if("DepartmentSite".equals(type)) {
                return folder("/departmentos");
            } else if("ScientificAreaSite".equals(type)) {
                return folder("/unit");
            } else if("ScientificCouncilSite".equals(type)) {
                return folder("/unit");
            } else if("ResearchUnitSite".equals(type)) {
                return folder("/unit");
            } else {
                return folder("/unit");
            }
        } else {
            return null;
        }
    }



    private CMSFolder folder(String string) {
        return Bennu.getInstance().getCmsFolderSet().stream()
                .filter(folder -> folder.getFunctionality().getFullPath().equals(string)).findAny().get();
    }

    @Override
    public TxMode getTxMode() {
        return TxMode.READ;
    }

    private void process(Site site, JsonObject json, Menu menu, MenuItem parent) {
        if (json.has("sections")) {
            for (JsonElement ell : json.get("sections").getAsJsonArray()) {
                JsonObject section = ell.getAsJsonObject();
                if (section.has("customPath")) {
                    // TODO Determine what to do here
                    continue;
                }
                MenuItem root = processItem(site, menu, parent, section.get("items").getAsJsonArray().get(0).getAsJsonObject());
                process(site, section, menu, root);
            }
        }
        if(json.has("bannerInfo")) {
            importBanners(site, json.getAsJsonObject("bannerInfo"));
        }
        if(json.has("managers")) {
            taskLog("managers '%s'", json.get("managers").toString());
            //TODO
        }
        if(json.has("layout")) {
            String layout = json.get("layout").getAsString();
            String homepageTemplate = "unitHomepageWithBannerIntro";
            if("BANNER_INTRO".equals(layout)) {
                homepageTemplate = "unitHomepageWithBannerIntro";
            } else if("BANNER_INTRO_COLLAPSED".equals(layout)) {
                homepageTemplate = "unitHomepageWithIntroFloat";
            } else if ("INTRO_BANNER".equals(layout)) {
                homepageTemplate = "unitHomepageWithIntroBanner";
            }
            site.setInitialPage(Page.create(site, menu, null, TITLE_HOMEPAGE, true, homepageTemplate,
                    Authenticate.getUser(), forType(UnitHomepageComponent.class)));
        }
    }

    private MenuItem processItem(Site site, Menu menu, MenuItem parent, JsonObject item) {
        Post post = FenixFramework.getDomainObject(item.get("id").getAsString());
        site.addPost(post);
        if (post.getName() != null) {
            post.setSlug(StringNormalizer.slugify(post.getName().getContent()));
        }
        if (post.getCreatedBy() == null) {
            post.setCreatedBy(Authenticate.getUser());
        }
        if (post.getCreationDate() == null) {
            post.setCreationDate(DateTime.now());
        }
        MenuItem menuItem = new MenuItem(menu);
        if (parent == null) {
            menu.addToplevelItems(menuItem);
        } else {
            menuItem.setParent(parent);
        }
        menuItem.setName(post.getName());
        menuItem.setPosition(item.get("order").getAsInt() + 100);
        Page page = new Page(site);
        if (site.getInitialPage() == null) {
            site.setInitialPage(page);
        }
        menuItem.setPage(page);
        page.setName(post.getName());
        page.setCanViewGroup(AnyoneGroup.get());
        page.setPublished(true);
        page.addComponents(new StaticPost(post));
        page.setTemplate(site.getTheme().templateForType("view"));
        if (item.has("files")) {
            int idx = 0;
            for (JsonElement fileEl : item.get("files").getAsJsonArray()) {
                GroupBasedFile file = FenixFramework.getDomainObject(fileEl.getAsString());
                PostFile postFile = new PostFile();
                postFile.setFiles(file);
                postFile.setPost(post);
                postFile.setIndex(idx++);
            }
        }
        return menuItem;
    }

    private void installThemes() {
        installTheme("fenixedu-learning-theme");
        installTheme("fenixedu-homepages-theme");
        installTheme("fenixedu-units-theme");
    }

    private void installTheme(String themeName) {
        InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/resources/WEB-INF/" + themeName + ".zip");
        ZipInputStream zin = new ZipInputStream(in);
        CMSThemeLoader.createFromZipStream(zin);
    }

    private Post importBanners(Site site, JsonObject bannerInfoJson) {
        Category bannerCategory = site.getOrCreateCategoryForSlug("banner", BANNER_NAME);
        Post post = new Post(site);

        if(bannerInfoJson.has("banners") && bannerInfoJson.get("banners").isJsonArray()) {
            bannerInfoJson.get("banners").getAsJsonArray().forEach(bannerJsonElement-> {
                JsonObject bannerJson = bannerJsonElement.getAsJsonObject();
                Post postBanner = new Post(site);
                postBanner.setName(BANNER_NAME);
                postBanner.addCategories(bannerCategory);
                postBanner.setActive(true);
                postBanner.setMetadata(bannerMetadata(postBanner, siteBannerMetadata(bannerInfoJson), bannerJson));
            });
        }

        return post;
    }

    private PostMetadata siteBannerMetadata(JsonObject bannerInfoJson) {
        PostMetadata metadata = new PostMetadata();
        metadata = addBooleanIfPresent(metadata, bannerInfoJson, "showIntroduction");
        metadata = addBooleanIfPresent(metadata, bannerInfoJson, "showBanner");
        metadata = addBooleanIfPresent(metadata, bannerInfoJson, "showPersonalizedLogo");
        metadata = addBooleanIfPresent(metadata, bannerInfoJson, "showAnnouncements");
        metadata = addBooleanIfPresent(metadata, bannerInfoJson, "showEvents");
        return metadata;
    }

    private PostMetadata bannerMetadata(Post post, PostMetadata metadata, JsonObject bannerJson) {
        metadata = addStringIfPresent(metadata, bannerJson, "link");
        metadata = addStringIfPresent(metadata, bannerJson, "color");
        metadata = addStringIfPresent(metadata, bannerJson, "weight");
        metadata = addStringIfPresent(metadata, bannerJson, "weightPercentage");
        metadata = addStringIfPresent(metadata, bannerJson, "repeatType");

        if(bannerJson.has("backgroundImage")) {
            GroupBasedFile backgroundImage = getDomainObject(bannerJson.get("backgroundImage").getAsString());
            post.getAttachments().putFile(backgroundImage, 0);
            //Cannot find annotation method 'urlPatterns()' in type 'WebServlet':
            //metadata = metadata.with("backgroundImage", FileDownloadServlet.getDownloadUrl(backgroundImage));
            String backGroundImageUrl = getDownloadUrl(backgroundImage);
            metadata = metadata.with("backgroundImage", backGroundImageUrl);

        }

        if(bannerJson.has("mainImage")) {
            GroupBasedFile mainImage = getDomainObject(bannerJson.get("mainImage").getAsString());
            post.getAttachments().putFile(mainImage, 0);
            //Cannot find annotation method 'urlPatterns()' in type 'WebServlet':
            //metadata = metadata.with("mainImage", FileDownloadServlet.getDownloadUrl(mainImage));
            String mainImageUrl = getDownloadUrl(mainImage);
            metadata = metadata.with("mainImage", mainImageUrl);
        }

        return metadata;
    }

    public static String getDownloadUrl(GenericFile file) {
        return CoreConfiguration.getConfiguration().applicationUrl() + SERVLET_PATH + file.getExternalId() + "/"
                + file.getFilename();
    }


    private PostMetadata addBooleanIfPresent(PostMetadata postMetadata, JsonObject jsonObject, String property) {
        return jsonObject.has(property) ? postMetadata.with(property, jsonObject.get(property).getAsBoolean()) : postMetadata;
    }

    private PostMetadata addStringIfPresent(PostMetadata postMetadata, JsonObject jsonObject, String property) {
        return jsonObject.has(property) ? postMetadata.with(property, jsonObject.get(property).getAsString()) : postMetadata;
    }
}
