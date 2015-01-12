package pt.ist.learning;

import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.cms.domain.Site;
import pt.ist.fenix.domain.unit.UnitSite;
import pt.ist.fenixframework.Atomic;

/**
 * Created by borgez on 1/12/15.
 */
public class ListUnitSites extends CustomTask {
    @Override
    public void runTask() throws Exception {
        Bennu.getInstance().getSitesSet().stream().forEach(site -> {
            if (site instanceof UnitSite) {
                System.out.println(String.format("{ edit: %s, link: %s}", site.getEditUrl(), site.getFullUrl()));
                taskLog("{ edit: %s, link: %s}", site.getEditUrl(), site.getFullUrl());
            }
        });
    }

    @Override
    public Atomic.TxMode getTxMode() {
        return Atomic.TxMode.READ;
    }
}
