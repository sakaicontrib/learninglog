package org.sakaiproject.learninglog.tool.entityprovider;

import java.util.Map;
import java.util.HashMap;

import org.apache.log4j.Logger;
import org.sakaiproject.entitybroker.DeveloperHelperService;
import org.sakaiproject.entitybroker.EntityReference;
import org.sakaiproject.entitybroker.EntityView;
import org.sakaiproject.entitybroker.entityprovider.EntityProvider;
import org.sakaiproject.entitybroker.entityprovider.annotations.EntityCustomAction;
import org.sakaiproject.entitybroker.entityprovider.capabilities.ActionsExecutable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.AutoRegisterEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Createable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Describeable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Inputable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Outputable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Resolvable;
import org.sakaiproject.entitybroker.entityprovider.extension.Formats;
import org.sakaiproject.entitybroker.util.AbstractEntityProvider;
import org.sakaiproject.learninglog.api.LearningLogManager;
import org.sakaiproject.learninglog.api.SakaiProxy;
import org.sakaiproject.learninglog.api.Roles;

import lombok.Setter;

@Setter
public class LearningLogRoleEntityProvider extends AbstractEntityProvider implements EntityProvider, AutoRegisterEntityProvider, 
	Inputable, Outputable, Resolvable,Createable, Describeable, ActionsExecutable {
	
	public final static String ENTITY_PREFIX = "learninglog-role";

	protected final Logger LOG = Logger.getLogger(LearningLogRoleEntityProvider.class);
	
	private DeveloperHelperService developerService = null;
	private LearningLogManager learningLogManager;
	
	public void init() {}
	
	private Map<String,String> sample = new HashMap<String,String>();
	
	public String createEntity(EntityReference ref, Object entity, Map<String, Object> params) {

		if(LOG.isDebugEnabled()) LOG.debug("createEntity");
		
		String userId = developerService.getCurrentUserId();
		
		Map<String,String> map = new HashMap<String,String>();

		for (String name : params.keySet()) {

			Object value = params.get(name);
			
			if(Roles.STUDENT.equals(value)) {
				map.put(name,(String) value);
            } else if(Roles.TUTOR.equals(value)) {
				map.put(name,(String) value);
            }
		}
		
		String siteId = (String) params.get("siteId");
		
		if(learningLogManager.saveRoles(siteId,map)) {
			return "SUCCESS";
		} else {
			return "FAIL";
        }
	}

	public Object getSampleEntity() {
		return sample;
	}

	public String getEntityPrefix() {
		return ENTITY_PREFIX;
	}
	
	public String[] getHandledOutputFormats() {
	    return new String[] { Formats.JSON };
	}
	
	public String[] getHandledInputFormats() {
        return new String[] { Formats.HTML, Formats.JSON, Formats.FORM };
    }

	public Object getEntity(EntityReference ref) {

		String siteId = ref.getId();
		return learningLogManager.getRoles(siteId);
	}
}
