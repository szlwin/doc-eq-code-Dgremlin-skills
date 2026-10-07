package com.dec.lite.parser;

import com.dec.lite.model.DecProject;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/** Secure DEC XML frontend. Both frontends enter the same canonical AST loader. */
public final class DecXmlParser {
    public DecProject parse(Path path) {
        if (path == null) throw new IllegalArgumentException("XML path is required");
        Path input = path.toAbsolutePath().normalize();
        if (!Files.exists(input)) throw new DecParseException(input, "input does not exist");
        DecProject project = new DecProject();
        try {
            List<Path> files;
            if (Files.isRegularFile(input)) files = List.of(input);
            else try (Stream<Path> stream = Files.walk(input)) {
                files = stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".xml"))
                        .sorted().toList();
            }
            if (files.isEmpty()) throw new DecParseException(input, "no .xml files found");
            for (Path file : files) {
                try { DecYamlParser.addRoot(convert(read(file)), file, project); }
                catch (DecParseException failure) { throw failure; }
                catch (IllegalArgumentException failure) { throw new DecParseException(file, failure.getMessage(), failure); }
            }
            return project.freeze();
        } catch (DecParseException failure) { throw failure; }
        catch (IOException | RuntimeException failure) { throw new DecParseException(input, "cannot load DEC XML: " + failure.getMessage(), failure); }
    }

    private static Element read(Path file) {
        try {
            if (Files.size(file) > 8_000_000) throw new DecParseException(file, "XML document exceeds 8 MB limit");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void warning(org.xml.sax.SAXParseException error) throws SAXException { throw error; }
                @Override public void error(org.xml.sax.SAXParseException error) throws SAXException { throw error; }
                @Override public void fatalError(org.xml.sax.SAXParseException error) throws SAXException { throw error; }
            });
            return builder.parse(file.toFile()).getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException failure) {
            String location = failure instanceof org.xml.sax.SAXParseException marked
                    ? " at line " + marked.getLineNumber() + ", column " + marked.getColumnNumber() : "";
            throw new DecParseException(file, "invalid or unsafe DEC XML" + location + ": " + failure.getMessage(), failure);
        }
    }

    private static Map<String, Object> convert(Element root) {
        String kind = switch (root.getTagName()) {
            case "orm-config" -> "config";
            case "orm-data-mapping" -> "data";
            case "orm-view-mapping" -> "view";
            case "orm-rule-mapping" -> "rule";
            case "api-config" -> "api";
            case "enum-config" -> "enum";
            case "systems" -> "systems";
            case "business-config" -> "business";
            default -> throw new IllegalArgumentException("unsupported DEC XML root: " + root.getTagName());
        };
        Map<String, Object> document = m("kind", kind, "version", "dec/v1");
        switch (kind) {
            case "config" -> config(root, document);
            case "data" -> document.put("datas", data(root));
            case "view" -> document.put("views", views(root));
            case "rule" -> document.put("ruleViews", rules(root));
            case "api" -> document.put("apis", apis(root));
            case "enum" -> document.put("enums", enums(root));
            case "systems" -> document.put("systems", systems(root));
            case "business" -> document.put("business", business(root));
            default -> throw new IllegalStateException(kind);
        }
        return document;
    }

    private static void config(Element root, Map<String, Object> out) {
        shape(root, "", "orm-datasource-info orm-data-file-info orm-relation-file-info orm-view-file-info orm-rule-file-info orm-service-info api-file-info enum-file-info system-file-info business-file-info orm-connection-info");
        Element sources = first(root, "orm-datasource-info");
        if (sources != null) {
            shape(sources, "default", "orm-datasource");
            List<Map<String, Object>> values = new ArrayList<>();
            for (Element source : children(sources, "orm-datasource")) {
                shape(source, "name", "name driver-class url username password");
                Map<String, Object> value = m("name", required(source, "name"), "type", content(firstRequired(source, "name")));
                for (String field : List.of("driver-class", "url", "username", "password"))
                    if (first(source, field) != null) value.put(camel(field), content(first(source, field)));
                values.add(value);
            }
            Map<String, Object> info = m("dataSources", values);
            putAttr(info, "default", sources, "default");
            out.put("dataSourceInfo", info);
        }
        String[][] fileSections = {
                {"orm-data-file-info", "orm-file", "dataFiles"}, {"orm-relation-file-info", "orm-file", "relationFiles"},
                {"orm-view-file-info", "orm-file", "viewFiles"}, {"orm-rule-file-info", "orm-file", "ruleFiles"},
                {"orm-service-info", "orm-file", "serviceFiles"}, {"api-file-info", "api-file", "apiFiles"},
                {"enum-file-info", "enum-file", "enumFiles"}, {"system-file-info", "system-file", "systemFiles"},
                {"business-file-info", "business-file", "businessFiles"}
        };
        for (String[] section : fileSections) {
            Element parent = first(root, section[0]);
            if (parent == null) continue;
            shape(parent, "", section[1]);
            List<Map<String, Object>> paths = new ArrayList<>();
            for (Element child : children(parent, section[1])) {
                shape(child, "path", ""); paths.add(m("path", required(child, "path")));
            }
            out.put(section[2], paths);
        }
        Element connections = first(root, "orm-connection-info");
        if (connections != null) {
            shape(connections, "default", "orm-connection");
            List<Map<String, Object>> values = new ArrayList<>();
            for (Element connection : children(connections, "orm-connection")) {
                shape(connection, "name", "data-source-info property-info");
                Map<String, Object> value = m("name", required(connection, "name"));
                Element refs = firstRequired(connection, "data-source-info");
                shape(refs, "", "data-source");
                List<String> sourcesRefs = new ArrayList<>();
                for (Element ref : children(refs, "data-source")) {
                    shape(ref, "ref", ""); sourcesRefs.add(required(ref, "ref"));
                }
                value.put("dataSources", sourcesRefs);
                Element properties = first(connection, "property-info");
                if (properties != null) {
                    shape(properties, "", "property"); Map<String, Object> props = new LinkedHashMap<>();
                    for (Element prop : children(properties, "property")) {
                        shape(prop, "name value", ""); props.put(required(prop, "name"), required(prop, "value"));
                    }
                    if (!props.isEmpty()) value.put("properties", props);
                }
                values.add(value);
            }
            Map<String, Object> info = m("connections", values);
            putAttr(info, "default", connections, "default"); out.put("connectionInfo", info);
        }
    }

    private static List<Map<String, Object>> data(Element root) {
        shape(root, "", "data"); List<Map<String, Object>> values = new ArrayList<>();
        for (Element data : children(root, "data")) {
            shape(data, "name system class", "property-info table-info");
            Map<String, Object> value = m("name", required(data, "name"));
            putAttr(value, "system", data, "system"); putAttr(value, "className", data, "class");
            Element props = firstRequired(data, "property-info"); shape(props, "", "property");
            Map<String, Object> properties = new LinkedHashMap<>();
            for (Element prop : children(props, "property")) {
                shape(prop, "name type desc", "");
                Map<String, Object> definition = m("type", required(prop, "type"));
                putAttr(definition, "desc", prop, "desc");
                properties.put(required(prop, "name"), definition.size() == 1 ? definition.get("type") : definition);
            }
            value.put("properties", properties);
            Element tableInfo = first(data, "table-info");
            if (tableInfo != null) {
                shape(tableInfo, "", "table"); List<Map<String, Object>> tables = new ArrayList<>();
                for (Element table : children(tableInfo, "table")) {
                    shape(table, "name data-source key key-type", "column");
                    Map<String, Object> t = m("name", required(table, "name"), "dataSource", required(table, "data-source"),
                            "key", required(table, "key"), "keyType", required(table, "key-type"));
                    Map<String, Object> columns = new LinkedHashMap<>();
                    for (Element column : children(table, "column")) {
                        shape(column, "name ref-property type rel-enum", "");
                        Map<String, Object> c = m("ref", required(column, "ref-property"));
                        putAttr(c, "type", column, "type"); putAttr(c, "relEnum", column, "rel-enum");
                        columns.put(required(column, "name"), c.size() == 1 ? c.get("ref") : c);
                    }
                    t.put("columns", columns); tables.add(t);
                }
                value.put("tables", tables);
            }
            id(value, root, data, "DATA-" + slug(required(data, "name"))); values.add(value);
        }
        return values;
    }

    private static List<Map<String, Object>> views(Element root) {
        shape(root, "", "view"); List<Map<String, Object>> values = new ArrayList<>();
        for (Element view : children(root, "view")) {
            shape(view, "name system target-main class", "property-info");
            Map<String, Object> value = m("name", required(view, "name"), "system", required(view, "system"),
                    "targetMain", required(view, "target-main"));
            putAttr(value, "className", view, "class");
            Element properties = firstRequired(view, "property-info"); shape(properties, "", "property");
            value.put("properties", viewProperties(properties));
            id(value, root, view, "VIEW-" + slug(required(view, "name"))); values.add(value);
        }
        return values;
    }
    private static Map<String, Object> viewProperties(Element parent) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Element prop : children(parent, "property")) {
            shape(prop, "name ref-property relation data key rel-key rel-value desc", "property");
            Map<String, Object> definition = new LinkedHashMap<>();
            if (prop.hasAttribute("relation")) {
                definition.put("relation", required(prop, "relation"));
                definition.put("data", required(prop, "data"));
                definition.put("key", required(prop, "key"));
                definition.put("relKey", required(prop, "rel-key"));
                putAttr(definition, "relValue", prop, "rel-value");
                putAttr(definition, "desc", prop, "desc");
                definition.put("properties", viewProperties(prop));
            } else {
                definition.put("ref", required(prop, "ref-property"));
                putAttr(definition, "relValue", prop, "rel-value");
                putAttr(definition, "desc", prop, "desc");
            }
            result.put(required(prop, "name"), definition.size() == 1 ? definition.get("ref") : definition);
        }
        return result;
    }

    private static List<Map<String, Object>> rules(Element root) {
        shape(root, "", "rule-view-info"); List<Map<String, Object>> values = new ArrayList<>();
        for (Element view : children(root, "rule-view-info")) {
            shape(view, "name code desc view-ref api-ref dataSource", "rule");
            String name = required(view, "name");
            Map<String, Object> value = m("name", name, "code", view.hasAttribute("code") ? required(view, "code") : name,
                    "viewRef", required(view, "view-ref"));
            putAttr(value, "desc", view, "desc"); putAttr(value, "apiRef", view, "api-ref");
            putAttr(value, "dataSource", view, "dataSource");
            List<Map<String, Object>> definitions = new ArrayList<>();
            for (Element rule : children(view, "rule")) definitions.add(rule(rule, view, name));
            value.put("rules", definitions);
            id(value, root, view, "RV-" + slug(name)); values.add(value);
        }
        return values;
    }
    private static Map<String, Object> rule(Element rule, Element parent, String context) {
        shape(rule, "name type property pattern sql dataSource", "error-info customer-info customer-process");
        String name = required(rule, "name"); String type = required(rule, "type");
        Map<String, Object> value = m("name", name, "type", type.equals("grammer") ? "dsl" : type);
        putAttr(value, "property", rule, "property"); putAttr(value, "pattern", rule, "pattern");
        putAttr(value, "cmd", rule, "sql"); putAttr(value, "dataSource", rule, "dataSource");
        Element error = first(rule, "error-info");
        if (error != null) {
            shape(error, "code message level", ""); Map<String, Object> e = m("code", required(error, "code"), "message", required(error, "message"));
            if (error.hasAttribute("level")) e.put("level", number(required(error, "level")));
            value.put("error", e);
        }
        Element customer = first(rule, "customer-info");
        if (customer != null) {
            Map<String, Object> c = new LinkedHashMap<>(); NamedNodeMap attrs = customer.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) c.put(attrs.item(i).getNodeName(), attrs.item(i).getNodeValue());
            value.put("customer", c);
        }
        Element process = first(rule, "customer-process");
        if (process != null) value.put("process", content(process));
        id(value, parent, rule, "RULE-" + slug(context) + "-" + slug(name)); return value;
    }

    private static List<Map<String, Object>> enums(Element root) {
        shape(root, "", "enum"); List<Map<String, Object>> values = new ArrayList<>();
        for (Element enumeration : children(root, "enum")) {
            shape(enumeration, "name desc", "enum-value"); String name = required(enumeration, "name");
            Map<String, Object> value = m("name", name); putAttr(value, "desc", enumeration, "desc");
            List<Map<String, Object>> items = new ArrayList<>();
            for (Element item : children(enumeration, "enum-value")) {
                shape(item, "value name desc", ""); String raw = required(item, "value");
                Map<String, Object> v = m("value", number(raw), "name", required(item, "name"));
                putAttr(v, "desc", item, "desc");
                id(v, enumeration, item, "ENUM-VALUE-" + slug(name) + "-" + slug(raw)); items.add(v);
            }
            value.put("values", items); id(value, root, enumeration, "ENUM-" + slug(name)); values.add(value);
        }
        return values;
    }

    private static List<Map<String, Object>> apis(Element root) {
        shape(root, "", "api"); List<Map<String, Object>> values = new ArrayList<>();
        for (Element api : children(root, "api")) {
            shape(api, "name desc system url method", "request response");
            String name = required(api, "name"), system = required(api, "system");
            Map<String, Object> value = m("name", name, "system", system, "url", required(api, "url"),
                    "method", required(api, "method").toUpperCase(Locale.ROOT));
            putAttr(value, "desc", api, "desc");
            Element request = first(api, "request");
            if (request != null) {
                shape(request, "", "validation-info parameter"); Map<String, Object> req = new LinkedHashMap<>();
                List<Map<String, Object>> validations = validations(request);
                if (!validations.isEmpty()) req.put("validations", validations);
                List<Map<String, Object>> params = new ArrayList<>();
                for (Element param : children(request, "parameter")) {
                    shape(param, "name in type required desc default rel-enum", "validation-info");
                    String paramName = required(param, "name");
                    Map<String, Object> p = m("name", paramName, "in", required(param, "in"), "type", required(param, "type"));
                    putBoolean(p, "required", param, "required");
                    putAttr(p, "desc", param, "desc"); putAttr(p, "default", param, "default"); putAttr(p, "relEnum", param, "rel-enum");
                    List<Map<String, Object>> checks = validations(param);
                    if (!checks.isEmpty()) p.put("validations", checks);
                    id(p, request, param, "API-PARAM-" + slug(system) + "-" + slug(name) + "-" + slug(paramName));
                    params.add(p);
                }
                if (!params.isEmpty()) req.put("params", params);
                value.put("request", req);
            }
            Element response = first(api, "response");
            if (response != null) {
                shape(response, "type desc model-ref", "field");
                Map<String, Object> r = m("type", required(response, "type"));
                putAttr(r, "desc", response, "desc"); putAttr(r, "modelRef", response, "model-ref");
                List<Map<String, Object>> fields = new ArrayList<>();
                for (Element field : children(response, "field")) {
                    shape(field, "name type required desc rel-enum", "validation-info");
                    Map<String, Object> f = m("name", required(field, "name"), "type", required(field, "type"));
                    putBoolean(f, "required", field, "required");
                    putAttr(f, "desc", field, "desc"); putAttr(f, "relEnum", field, "rel-enum");
                    List<Map<String, Object>> checks = validations(field);
                    if (!checks.isEmpty()) f.put("validations", checks);
                    fields.add(f);
                }
                if (!fields.isEmpty()) r.put("fields", fields);
                value.put("response", r);
            }
            id(value, root, api, "API-" + slug(system) + "-" + slug(name)); values.add(value);
        }
        return values;
    }
    private static List<Map<String, Object>> validations(Element parent) {
        Element info = first(parent, "validation-info");
        if (info == null) return List.of();
        shape(info, "", "validation"); List<Map<String, Object>> values = new ArrayList<>();
        for (Element validation : children(info, "validation")) {
            shape(validation, "type value expression message", "value");
            Map<String, Object> value = m("type", required(validation, "type"));
            if (validation.hasAttribute("value")) value.put("value", number(required(validation, "value")));
            putAttr(value, "expression", validation, "expression"); putAttr(value, "message", validation, "message");
            List<Object> options = new ArrayList<>();
            for (Element option : children(validation, "value")) { shape(option, "", ""); options.add(content(option)); }
            if (!options.isEmpty()) value.put("values", options);
            values.add(value);
        }
        return values;
    }

    private static List<Map<String, Object>> systems(Element root) {
        shape(root, "", "system"); List<Map<String, Object>> values = new ArrayList<>();
        for (Element system : children(root, "system")) {
            shape(system, "name", "data-info view-info rule-file-info information-info model-access-info");
            String name = required(system, "name"); Map<String, Object> value = m("name", name);
            String[][] refs = {{"data-info", "data-ref", "dataRefs", "name"}, {"view-info", "view-ref", "viewRefs", "name"},
                    {"rule-file-info", "rule-file", "ruleFiles", "path"}};
            for (String[] section : refs) {
                Element info = first(system, section[0]); if (info == null) continue;
                shape(info, "", section[1]); List<String> list = new ArrayList<>();
                for (Element ref : children(info, section[1])) { shape(ref, section[3], ""); list.add(required(ref, section[3])); }
                value.put(section[2], list);
            }
            Element information = first(system, "information-info");
            if (information != null) {
                shape(information, "", "information"); List<Map<String, Object>> definitions = new ArrayList<>();
                for (Element item : children(information, "information")) {
                    shape(item, "name view-ref rule-ref rule-data expression", "change-data");
                    String infoName = required(item, "name"); Map<String, Object> i = m("name", infoName);
                    putAttr(i, "viewRef", item, "view-ref"); putAttr(i, "ruleRef", item, "rule-ref");
                    putAttr(i, "ruleData", item, "rule-data"); putAttr(i, "expression", item, "expression");
                    Element change = first(item, "change-data");
                    if (change != null) { shape(change, "", ""); i.put("changeData", content(change)); }
                    id(i, information, item, "INFO-" + slug(name) + "-" + slug(infoName)); definitions.add(i);
                }
                value.put("information", definitions);
            }
            Element accesses = first(system, "model-access-info");
            if (accesses != null) {
                shape(accesses, "", "model-access"); List<Map<String, Object>> models = new ArrayList<>();
                for (Element access : children(accesses, "model-access")) {
                    shape(access, "model-ref", "read write"); Map<String, Object> model = m("modelRef", required(access, "model-ref"));
                    for (String mode : List.of("read", "write")) {
                        List<Map<String, Object>> entries = new ArrayList<>();
                        for (Element entry : children(access, mode)) {
                            shape(entry, "path", "ref"); Map<String, Object> a = m("path", required(entry, "path"));
                            List<Map<String, Object>> targets = new ArrayList<>();
                            for (Element ref : children(entry, "ref")) {
                                shape(ref, "view property", ""); targets.add(m("view", required(ref, "view"), "property", required(ref, "property")));
                            }
                            if (targets.size() == 1) a.put("ref", targets.get(0));
                            else if (!targets.isEmpty()) a.put("refs", targets);
                            entries.add(a);
                        }
                        if (!entries.isEmpty()) model.put(mode, entries);
                    }
                    models.add(model);
                }
                value.put("modelAccess", models);
            }
            id(value, root, system, "SYS-" + slug(name)); values.add(value);
        }
        return values;
    }

    private static Map<String, Object> business(Element root) {
        shape(root, "name desc", "directory-info"); String name = required(root, "name");
        Map<String, Object> result = m("name", name); putAttr(result, "desc", root, "desc");
        Element directories = firstRequired(root, "directory-info"); shape(directories, "", "directory");
        List<Map<String, Object>> values = new ArrayList<>();
        for (Element directory : children(directories, "directory")) {
            shape(directory, "name type information-ref model-ref is-root", "subdirectory-info dependency-info action-info change-info");
            String directoryName = required(directory, "name");
            Map<String, Object> value = m("name", directoryName, "informationRef", required(directory, "information-ref"),
                    "modelRef", required(directory, "model-ref"));
            putAttr(value, "type", directory, "type"); putBoolean(value, "isRoot", directory, "is-root");
            Element subs = first(directory, "subdirectory-info");
            if (subs != null) {
                shape(subs, "", "subdirectory"); List<Map<String, Object>> edges = new ArrayList<>();
                for (Element sub : children(subs, "subdirectory")) {
                    shape(sub, "rel role information-ref", "back"); Map<String, Object> edge = m("rel", required(sub, "rel"));
                    putAttr(edge, "role", sub, "role"); putAttr(edge, "informationRef", sub, "information-ref");
                    Element back = first(sub, "back");
                    if (back != null) {
                        shape(back, "name", "action-info"); Map<String, Object> b = m("name", required(back, "name"));
                        List<Map<String, Object>> actions = actions(back, directoryName + "-BACK-" + required(back, "name"));
                        if (!actions.isEmpty()) b.put("actions", actions);
                        edge.put("back", b);
                    }
                    edges.add(edge);
                }
                value.put("subDirectories", edges);
            }
            Element dependencies = first(directory, "dependency-info");
            if (dependencies != null) {
                shape(dependencies, "", "dependency"); List<Map<String, Object>> deps = new ArrayList<>();
                for (Element dependency : children(dependencies, "dependency")) {
                    shape(dependency, "information-ref", ""); deps.add(m("informationRef", required(dependency, "information-ref")));
                }
                value.put("dependencies", deps);
            }
            List<Map<String, Object>> actionList = actions(directory, directoryName);
            if (!actionList.isEmpty()) value.put("actions", actionList);
            Element change = first(directory, "change-info");
            if (change != null) {
                shape(change, "information-ref", "");
                if (!change.hasAttribute("information-ref")) throw new IllegalArgumentException("legacy Change SQL is unsupported");
                value.put("change", m("informationRef", required(change, "information-ref")));
            }
            id(value, directories, directory, "DIR-" + slug(directoryName)); values.add(value);
        }
        result.put("directories", values);
        idRoot(result, root, "BUS-" + slug(name)); return result;
    }
    private static List<Map<String, Object>> actions(Element parent, String directoryName) {
        Element info = first(parent, "action-info"); if (info == null) return List.of();
        shape(info, "", "action"); List<Map<String, Object>> values = new ArrayList<>();
        for (Element action : children(info, "action")) {
            shape(action, "name system-ref rule-ref", "rule produce-info");
            String name = required(action, "name"); Map<String, Object> value = m("name", name);
            putAttr(value, "systemRef", action, "system-ref"); putAttr(value, "ruleRef", action, "rule-ref");
            List<Map<String, Object>> inline = new ArrayList<>();
            for (Element rule : children(action, "rule")) inline.add(rule(rule, action, directoryName + "-" + name));
            if (!inline.isEmpty()) value.put("rules", inline);
            Element infoProduce = first(action, "produce-info");
            if (infoProduce != null) {
                shape(infoProduce, "", "produce"); List<Map<String, Object>> produces = new ArrayList<>();
                for (Element produce : children(infoProduce, "produce")) {
                    shape(produce, "ref information-ref", ""); String ref = required(produce, "ref");
                    Map<String, Object> p = m("ref", ref); putAttr(p, "informationRef", produce, "information-ref");
                    id(p, infoProduce, produce, "PROD-" + slug(directoryName) + "-" + slug(name) + "-" + slug(ref));
                    produces.add(p);
                }
                value.put("produces", produces);
            }
            id(value, info, action, "ACT-" + slug(directoryName) + "-" + slug(name)); values.add(value);
        }
        return values;
    }

    private static Map<String, Object> m(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
    private static void putAttr(Map<String, Object> value, String key, Element element, String attr) {
        if (element.hasAttribute(attr)) value.put(key, element.getAttribute(attr));
    }
    private static void putBoolean(Map<String, Object> value, String key, Element element, String attr) {
        if (element.hasAttribute(attr)) {
            String raw = element.getAttribute(attr);
            if (!raw.equals("true") && !raw.equals("false")) throw new IllegalArgumentException("invalid boolean " + attr + "=" + raw);
            value.put(key, Boolean.valueOf(raw));
        }
    }
    private static Object number(String raw) {
        if (raw.matches("[-+]?\\d+")) try { return Long.parseLong(raw); } catch (NumberFormatException ignored) { return raw; }
        if (raw.matches("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+)")) try { return Double.parseDouble(raw); } catch (NumberFormatException ignored) { return raw; }
        return raw;
    }
    private static String camel(String raw) {
        String[] parts = raw.split("-"); StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) result.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        return result.toString();
    }
    private static String slug(String value) { return value.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "").toUpperCase(Locale.ROOT); }
    private static String content(Element element) { return element.getTextContent().replaceAll("^\\n+|\\n+$", ""); }
    private static String required(Element element, String name) {
        String value = element.getAttribute(name);
        if (value.isEmpty()) throw new IllegalArgumentException(element.getTagName() + "@" + name + " is required");
        return value;
    }
    private static Element firstRequired(Element parent, String tag) {
        Element found = first(parent, tag);
        if (found == null) throw new IllegalArgumentException(parent.getTagName() + "/" + tag + " is required");
        return found;
    }
    private static Element first(Element parent, String tag) {
        for (Element child : children(parent, null)) if (child.getTagName().equals(tag)) return child;
        return null;
    }
    private static List<Element> children(Element parent, String tag) {
        List<Element> values = new ArrayList<>(); NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) if (nodes.item(i) instanceof Element element && (tag == null || element.getTagName().equals(tag))) values.add(element);
        return values;
    }
    private static void shape(Element element, String attrs, String allowedChildren) {
        Set<String> attrSet = attrs.isBlank() ? Set.of() : Set.of(attrs.split(" "));
        Set<String> childSet = allowedChildren.isBlank() ? Set.of() : Set.of(allowedChildren.split(" "));
        NamedNodeMap actual = element.getAttributes();
        for (int i = 0; i < actual.getLength(); i++) if (!attrSet.contains(actual.item(i).getNodeName()))
            throw new IllegalArgumentException(element.getTagName() + " has unsupported attribute " + actual.item(i).getNodeName());
        for (Element child : children(element, null)) if (!childSet.contains(child.getTagName()))
            throw new IllegalArgumentException(element.getTagName() + " has unsupported child " + child.getTagName());
    }
    private static void id(Map<String, Object> value, Element parent, Element element, String generated) {
        String comment = null;
        for (Node node = element.getPreviousSibling(); node != null; node = node.getPreviousSibling()) {
            if (node instanceof Text text && text.getTextContent().isBlank()) continue;
            if (node instanceof Comment c && c.getData().trim().matches("dec-id: [A-Z][A-Z0-9-]*"))
                comment = c.getData().trim().substring("dec-id: ".length());
            break;
        }
        value.put("id", comment == null ? generated : comment);
    }
    private static void idRoot(Map<String, Object> value, Element root, String generated) {
        String comment = null;
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Text text && text.getTextContent().isBlank()) continue;
            if (node instanceof Comment c && c.getData().trim().matches("dec-id: [A-Z][A-Z0-9-]*"))
                comment = c.getData().trim().substring("dec-id: ".length());
            break;
        }
        value.put("id", comment == null ? generated : comment);
    }
}
