# Original Rule example fixture

Source: sibling repository `doc-eq-code-Dgremlin/dec-demo/src/main/resources/model/test-rule/orm-rule.xml`.

`rule-example.yaml` imports the 14 active rules through the design skill's XML importer, preserving the original rule order, commands and expressions. `systems.yaml` supplies the minimal RuleView owner for compiler tests. This is a Rule slice, not a complete application: it intentionally omits Data/View/config mappings.

From the skills repository root, regenerate with the official scripts:

```sh
python3 skills/dec-design/scripts/xml_to_yaml.py ../doc-eq-code-Dgremlin/dec-demo/src/main/resources/model/test-rule/orm-rule.xml -o dec-java-lite/fixtures/rule-compatibility/rule-example.yaml
python3 skills/dec-design/scripts/yaml_to_xml.py dec-java-lite/fixtures/rule-compatibility -o dec-java-lite/fixtures/rule-compatibility-xml --emit-id-comments
./dec-java-lite/mvnw -f dec-java-lite/pom.xml -pl dec-lite-action -am test
```

`RuleCompatibilityTest` runs both representations through the same generic Rule bridge. Its in-memory driver handles only the fixture's operations and command shapes; it is not a JDBC SQL translator.
