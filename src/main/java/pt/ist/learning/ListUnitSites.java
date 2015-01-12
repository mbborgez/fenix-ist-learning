package pt.ist.learning;

import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.cms.domain.Category;
import org.fenixedu.cms.domain.Site;
import pt.ist.fenix.domain.unit.UnitSite;
import pt.ist.fenixframework.Atomic;

import java.util.stream.Collectors;

/**
 * Created by borgez on 1/12/15.
 */
public class ListUnitSites extends CustomTask {
    @Override
    public void runTask() throws Exception {
        Bennu.getInstance().getSitesSet().stream().filter(site->site instanceof UnitSite)
                .collect(Collectors.groupingBy(site->((UnitSite) site).getUnit().getClass())).forEach((k, v) -> {
            String str = String.format("{ \n type: %s, \n sites: [ %s ] \n }",
                    k.getSimpleName(), v.stream().map(site -> String.format("{ edit: %s, link: %s}", site.getEditUrl(),
                            site.getFullUrl())).collect(Collectors.joining(",\n")));

            System.out.println(str);
        });
    }

    @Override
    public Atomic.TxMode getTxMode() {
        return Atomic.TxMode.READ;
    }
}
