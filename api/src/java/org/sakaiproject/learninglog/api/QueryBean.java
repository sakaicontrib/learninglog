/*************************************************************************************
 * Copyright 2006, 2008 Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.

 *************************************************************************************/

package org.sakaiproject.learninglog.api;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class QueryBean {
	
	private String queryString;

	private List<String> visibilities;

	private long initDate;

	private long endDate;

	private String creator;

	private String siteId;
	
	private String caller;

	public QueryBean() {
		
		visibilities = new ArrayList<String>(); // this mean no filter by visibility
		initDate = -1; // this mean no filter by initDate;
		endDate = -1; // this mean no filter by endDate
		creator = "";
		siteId = "";
		caller = "";
	}

	public boolean hasConditions() {
		return siteId.length() > 0 || visibilities.size() > 0 || initDate != -1 || endDate != -1;
	}

	public boolean queryBySiteId() {
		return !siteId.equals("");
	}

	public boolean queryByVisibility() {
		return visibilities.size() > 0;
	}

	public void setVisibilities(String[] visibilities) {
		
		this.visibilities.clear();
		
		for(String v : visibilities) {
			this.visibilities.add(v);
		}
	}

	public boolean queryByInitDate() {
		return initDate != -1;
	}

	public boolean queryByEndDate() {
		return endDate != -1;
	}

	public boolean queryByCreator() {
		return ! creator.trim().equals("");
	}
}
