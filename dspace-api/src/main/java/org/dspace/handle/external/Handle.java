/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.handle.external;


import org.dspace.handle.HandlePlugin;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

import javax.xml.bind.annotation.XmlElement;
import java.util.UUID;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.dspace.handle.external.ExternalHandleConstants.MAGIC_BEAN;

public class Handle {

    public String handle;
    public String url;
    public String title;
    public String repository;
    public String submitdate;
    public String reportemail;
    public String subprefix;
    public String datasetName;
    public String datasetVersion;
    public String query;
    public String token;

    public Handle(){

    }

    public Handle(String handle, String url, String title, String repository, String submitdate, String reportemail, String datasetName, String datasetVersion, String query, String token, String subprefix) {
        this.handle = handle;
        this.url = url;
        this.title = title;
        this.repository = repository;
        this.submitdate = submitdate;
        this.reportemail = reportemail;
        this.datasetName = datasetName;
        this.datasetVersion = datasetVersion;
        this.query = query;
        this.token = token;
        this.subprefix = subprefix;
    }

    public Handle(String handle, String magicURL){
        this.handle = handle;
        //similar to HandlePlugin
        String[] splits = magicURL.split(MAGIC_BEAN,10);
        this.url = splits[splits.length - 1];
        this.title = splits[1];
        this.repository = splits[2];
        this.submitdate = splits[3];
        this.reportemail = splits[4];
        if(isNotBlank(splits[5])) {
            this.datasetName = splits[5];
        }
        if(isNotBlank(splits[6])) {
            this.datasetVersion = splits[6];
        }
        if(isNotBlank(splits[7])) {
            this.query = splits[7];
        }
        if(isNotBlank(splits[8])){
            this.token = splits[8];
        }
        this.subprefix = handle.split("/",2)[1].split("-",2)[0];
    }

    public String getMagicUrl(){
        return this.getMagicUrl(this.title, this.submitdate, this.reportemail, this.datasetName, this.datasetVersion,
                this.query, this.url);
    }

    public String getMagicUrl(String title, String submitdate, String reportemail, String datasetName,
                              String datasetVersion, String query, String url){
        String magicURL = "";
        String token = UUID.randomUUID().toString();

        for (String part : new String[]{title, HandlePlugin.getRepositoryName(), submitdate, reportemail, datasetName,
                datasetVersion, query, token, url}){
            if(isBlank(part)){
                //optional dataset etc...
                part = "";
            }
            magicURL += MAGIC_BEAN + part;
        }
        return magicURL;
    }

    public String getHandle() {
        return HandlePlugin.getCanonicalHandlePrefix() + handle;
    }

    public void setHandle(String handle){
        this.handle = handle.replace(HandlePlugin.getCanonicalHandlePrefix(),"");
    }
}
