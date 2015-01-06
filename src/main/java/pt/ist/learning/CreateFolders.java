package pt.ist.learning;

import java.util.Locale;

import org.fenixedu.bennu.core.domain.NashornStrategy;
import org.fenixedu.bennu.portal.domain.MenuContainer;
import org.fenixedu.bennu.portal.domain.MenuFunctionality;
import org.fenixedu.bennu.portal.domain.PortalConfiguration;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.cms.domain.CMSFolder;
import org.fenixedu.cms.domain.CMSFolder.FolderResolver;
import org.fenixedu.commons.i18n.LocalizedString;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

public class CreateFolders extends CustomTask {

    @Override
    public void runTask() throws Exception {
        createFolder("disciplinas",
                "https://gist.githubusercontent.com/jcarvalho/b6fa8afadc16fb8731e9/raw/440ef7d5f20369ea6c469b60f1f1e5582d7f0acc/gistfile1.js");
        createFolder("homepage",
                "https://gist.githubusercontent.com/jcarvalho/13f510e3dbf7a53552b7/raw/599b92e0a25c25c0363365930e28d83e63ae1933/homepages.js");
        createFolder("unit",
                "https://gist.githubusercontent.com/mbborgez/1ecd3dedee19d15202e1/raw/c8e4eae83a2924114b1a5e26c272570dad0f1799/UnitSiteFolder.js");
    }

    private void createFolder(String path, String url) {
        MenuContainer root = PortalConfiguration.getInstance().getMenu();
        MenuFunctionality functionality = root.findFunctionalityWithPath(new String[] { path });
        if (functionality != null) {
            taskLog("Deleting functionality %s (%s)\n", functionality, functionality.getFullPath());
            functionality.delete();
        }
        String code = new RestTemplate().getForObject(url, String.class);

        LocalizedString description = new LocalizedString(Locale.getDefault(), StringUtils.capitalize(path));
        CMSFolder folder = new CMSFolder(root, path, description);
        folder.setResolver(new NashornStrategy<CMSFolder.FolderResolver>(FolderResolver.class, code));
    }

}
