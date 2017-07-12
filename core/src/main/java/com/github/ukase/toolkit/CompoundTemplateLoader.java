/*
 * Copyright (c) 2015 Konstantin Lepa <konstantin+ukase@lepabox.net>
 *
 * This file is part of Ukase.
 *
 *  Ukase is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.ukase.toolkit;

import com.github.jknack.handlebars.io.AbstractTemplateLoader;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.jknack.handlebars.io.StringTemplateSource;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.github.jknack.handlebars.io.TemplateSource;
import com.github.ukase.config.UkaseSettings;
import com.github.ukase.toolkit.jar.ZipTemplateSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Component
public class CompoundTemplateLoader extends AbstractTemplateLoader {
    private static final String IMAGE_AS_PAGE = "default - image as page";
    private static final String UPLOADED_RESOURCES_PREFIX = "upload://";
    private static final TemplateSource IMAGE_AS_PAGE_TEMPLATE;
    private static final String TEMPLATE_POSTFIX = ".hbs";

    static {
        IMAGE_AS_PAGE_TEMPLATE =
                new StringTemplateSource(IMAGE_AS_PAGE, StaticUtils.readStringFile(getStream()));
    }

    private static InputStream getStream() {
        return CompoundTemplateLoader.class.getResourceAsStream("image-as-page.hbs");
    }

    private final ZipFile zip;
    private final Map<String, ZipEntry> resources = new HashMap<>();
    private final Map<String, UploadedTemplateSource> uploadedResources = new HashMap<>();
    private final TemplateLoader externalLoader;

    @Autowired
    public CompoundTemplateLoader(UkaseSettings settings) throws IOException {
        log.info("Starting with settings: ", settings);
        log.info(settings.toString(), settings);
        File templates = settings.getTemplates();
        externalLoader =  templates == null ? null : new FileTemplateLoader(templates);

        if (settings.getJar() == null) {
            zip = null;
            return;
        }

        zip = new ZipFile(settings.getJar());
        zip.stream().forEach(this::registerResource);
    }

    @Override
    public TemplateSource sourceAt(String location) throws IOException {
        if (IMAGE_AS_PAGE.equals(location)) {
            //TODO extract loader for default templates (if there were more than 1)
            return IMAGE_AS_PAGE_TEMPLATE;
        }
        if (isUploadedResource(location)) {
            return uploadedResources.get(location + TEMPLATE_POSTFIX);
        }
        try {
            if (externalLoader == null) {
                return getTemplateSource(location, null);
            }
            return externalLoader.sourceAt(location);
        } catch (FileNotFoundException e) {
            return getTemplateSource(location, e);
        }
    }

    @Override
    public void setSuffix(String suffix) {
        externalLoader.setSuffix(suffix);
        super.setSuffix(suffix);
    }

    @Override
    public void setPrefix(String prefix) {
        externalLoader.setPrefix(prefix);
        super.setPrefix(prefix);
    }

    public boolean hasResource(String location) {
        if (isUploadedResource(location)) {
            return uploadedResources.containsKey(location);
        }
        return resources.containsKey(location);
    }

    public InputStream getResource(String location) throws IllegalStateException {
        if (isUploadedResource(location)) {
            String value = uploadedResources.get(location).content();
            if (value == null) {
                return null;
            }
            return new ByteArrayInputStream(value.getBytes());
        }

        ZipEntry resource = resources.get(location);
        if (resource == null) {
            return null;
        }
        try {
            return getResource(resource);
        } catch (IOException e) {
            throw new IllegalStateException("Wrong configuration", e);
        }
    }

    public Collection<String> getResources(Predicate<String> filter) {
        return resources.keySet().stream().filter(filter).collect(Collectors.toList());
    }

    public void uploadResource(String resourceName, String resource) {
        UploadedTemplateSource template = new UploadedTemplateSource(resource, resourceName);
        uploadedResources.put(UPLOADED_RESOURCES_PREFIX + resourceName + TEMPLATE_POSTFIX, template);
    }

    private InputStream getResource(ZipEntry resource) throws IOException {
        return zip.getInputStream(resource);
    }

    private void registerResource(ZipEntry entry) {
        resources.put(entry.getName(), entry);
    }

    private TemplateSource getTemplateSource(String location, FileNotFoundException e) throws FileNotFoundException {
        location = resolve(location);
        if (location.startsWith("/")) {
            location = location.substring(1);
        }
        ZipEntry entry = zip.getEntry(location);
        if (entry == null) {
            if (e == null) {
                throw new IllegalStateException("File not found in jar, while no dir configuration " + location);
            }
            throw e;
        }
        return new ZipTemplateSource(zip, entry);
    }

    private boolean isUploadedResource(String location) {
        return location != null && location.startsWith(UPLOADED_RESOURCES_PREFIX);
    }

    private static class UploadedTemplateSource implements TemplateSource {
        private final String content;
        private final String name;
        private final long lastModified;

        UploadedTemplateSource(String content, String name) {
            this.content = content;
            this.name = name;
            this.lastModified = System.currentTimeMillis();
        }

        @Override
        public String content() {
            return content;
        }

        @Override
        public String filename() {
            return name;
        }

        @Override
        public long lastModified() {
            return lastModified;
        }
    }
}
