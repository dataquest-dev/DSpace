package org.dspace.content.clarin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ClarinFeaturedService {

    public ClarinFeaturedService() {
    }

    private String name;
    private String url;
    private String description;
    private List<ClarinFeaturedServiceLink> featuredServiceLinks;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ClarinFeaturedServiceLink> getFeaturedServiceLinks() {
        if (Objects.isNull(featuredServiceLinks)) {
            featuredServiceLinks = new ArrayList<>();
        }
        return featuredServiceLinks;
    }

    public void setFeaturedServiceLinks(List<ClarinFeaturedServiceLink> featuredServiceLinks) {
        this.featuredServiceLinks = featuredServiceLinks;
    }
}
