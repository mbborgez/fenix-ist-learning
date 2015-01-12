package pt.ist.fenix.domain.unit.components;

import org.fenixedu.academic.domain.organizationalStructure.Unit;
import org.fenixedu.cms.domain.Page;
import org.fenixedu.cms.domain.Site;
import org.fenixedu.cms.domain.component.CMSComponent;
import org.fenixedu.cms.exceptions.ResourceNotFoundException;

import pt.ist.fenix.domain.unit.UnitSite;

public abstract class UnitSiteComponent implements CMSComponent {

    protected Unit unit(Page page) {
        if (page.getSite() instanceof UnitSite) {
            return ((UnitSite) page.getSite()).getUnit();
        }
        throw new ResourceNotFoundException();
    }

    protected boolean supportsSite(Site site) {
        return site instanceof UnitSite;
    }

}
