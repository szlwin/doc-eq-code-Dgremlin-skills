# Development and verification handbook

[Back to documentation home](README.md)

This handbook is organized around one real change: check the design first, then generate the application, and finally confirm the result with tests and Binding evidence. Unless noted otherwise, commands are run from the repository root. If the XML has been modified by an external tool, read the [sync guide](xml-to-yaml-sync.md) first instead of regenerating and overwriting those changes.

## 1. Prepare the environment first

- Python 3, with the two Skills' dependencies installed: PyYAML 6.x and jsonschema 4.x.
- JDK 21, with `JAVA_HOME` pointing to the matching JDK.
- Use the repository Maven Wrapper; the first build needs to be able to download Maven and dependencies.
- Running the order demo and real-database tests requires MySQL and a dedicated database.

```sh
python3 -m venv .venv
. .venv/bin/activate
python3 -m pip install -r skills/dec-design/requirements.txt \
  -r skills/dec-implementation/requirements.txt
java -version
./dec-java-lite/mvnw -version
```

## 2. Check the design first, then generate derived artifacts

```sh
python3 skills/dec-design/scripts/validate_dec_yaml.py demo/order-payment-yaml
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml --check
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml \
  -o demo/order-payment-xml --emit-id-comments
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml \
  -o demo/order-payment-app/src/main/resources/dec/design --emit-id-comments
python3 skills/dec-design/scripts/render_dec_graph.py demo/order-payment-yaml -o demo/design-html
```

Passing structure/reference validation does not mean the Runtime can execute every extension. When adding a Rule, Action, or DataSource, check both the compiler's capability and the actual adapters.

## 3. Build the framework and generate the application skeleton

```sh
./dec-java-lite/mvnw -f dec-java-lite/pom.xml install -DskipTests
```

After a successful build, locate the CLI from the actual artifacts (excluding sources, Javadoc, and test attachments):

```sh
# Pick the unique CLI main artifact, excluding -sources/-javadoc attachments; stop and inspect target/ if stale versions remain.
dec_cli_jar=$(python3 - <<'PYCLI'
from pathlib import Path
jars = sorted(p for p in Path('dec-java-lite/dec-lite-cli/target').glob('dec-lite-cli-*.jar')
              if not p.name.endswith(('-sources.jar', '-javadoc.jar', '-tests.jar')))
if len(jars) != 1:
    raise SystemExit(f'need exactly one CLI jar, found {len(jars)}; check the build output and retry')
print(jars[0])
PYCLI
)
```

Once located, run the following in the same shell; if the jar cannot be found or there are multiple jars, first check the build result and any leftover old versions.

```sh
java -jar "$dec_cli_jar" inspect \
  --yaml demo/order-payment-yaml
java -jar "$dec_cli_jar" generate \
  --yaml demo/order-payment-yaml \
  --output demo/order-payment-app \
  --base-package com.example.orderpayment
```

The `-DskipTests` here only serves to install dependencies first; it is not proof that tests passed. The generator maintains its own manifest scope, and handwritten code still has to be adjusted to the changed interfaces. The XML is produced by the previous section's Converter; the Java generator does not automatically collect it from arbitrary paths.

To experiment with a standalone API grouping configuration, use a new output directory:

```sh
java -jar "$dec_cli_jar" generate \
  --yaml demo/order-payment-yaml \
  --output /tmp/dec-configured-demo \
  --base-package com.example.orderpayment \
  --generation-config demo/order-payment-generation.properties
```

The sample configuration splits out a payment sub-resource group. The experiment directory is a generated skeleton and does not automatically copy the current demo's handwritten implementation; when adopting it into the real project, migrate the ServiceImpl/Bean/Binding.

## 4. Run through the order payment demo

First prepare a dedicated database in MySQL and set the connection parameters:

```sh
export DEC_DB_URL='jdbc:mysql://127.0.0.1:3306/dec_query_demo?useSSL=false&allowPublicKeyRetrieval=true'
export DEC_DB_USERNAME='root'
# Set DEC_DB_PASSWORD in your local environment; avoid putting credentials into command history or docs.
./dec-java-lite/mvnw -f demo/order-payment-app/pom.xml spring-boot:run
```

The sample URL is for local development. On startup the application performs schema initialization and creates the `dec_demo_*` tables; it does not automatically create a database that does not exist in the URL. Connection, migration, and credential management for production environments is decided by the deployment plan.

A demo path that fails, rolls back, and then succeeds:

```sh
curl -X POST http://localhost:8080/api/orders/101/submit \
  -H 'Content-Type: application/json' \
  -d '{"status":1,"type":1,"userId":1,"productId":7,"productName":"Demo item","productPrice":14.95,"productCount":2,"remark":"demo-order"}'
curl -X POST http://localhost:8080/api/orders/101/payment
curl -X POST http://localhost:8080/api/orders/101/payment/result \
  -H 'Content-Type: application/json' -d '{"resultCode":2}'
curl http://localhost:8080/api/orders/101
curl -X POST http://localhost:8080/api/orders/101/payment/back
curl -X POST http://localhost:8080/api/orders/101/payment/result \
  -H 'Content-Type: application/json' -d '{"resultCode":1}'
```

Use an order ID that is not taken yet; existing data may trigger duplicate-operation checks. `resultCode` 1 means success and 2 means failure. For business query calls see [demo query examples](order-payment-demo.md#7-query-orders-by-business-stage).

## 5. Choose the verification scope based on the change

| Change | Verification to run |
| --- | --- |
| Documentation only | Link, command, code path, and fact checks |
| YAML fields/references/rules | Design validation, Converter, Java compilation, related behavior tests |
| XML sync | Pre-checks, directory validation after the actual merge, regeneration, and related tests |
| HTML renderer | Rendering tests, regeneration drift checks, browser interaction checks |
| Java generator/templates | Generator tests, snapshots, generated-project compilation, applicable API tests |
| Transactions/adapters/queries | Framework tests, real-database scenarios, failure and rollback tests |
| Binding/implementation declarations | Full coverage, Java symbols, this run's test report, and digest evidence |

Common test commands:

```sh
python3 -m unittest discover -s skills/dec-design/tests
python3 -m unittest discover -s skills/dec-implementation/tests
./dec-java-lite/mvnw -f dec-java-lite/pom.xml verify
./dec-java-lite/mvnw -f demo/order-payment-app/pom.xml test

# Verifies only the business-query integration; still requires the real demo MySQL.
./dec-java-lite/mvnw -f demo/order-payment-app/pom.xml -Dtest=OrderQueryTest test
```

Demo tests clean up `dec_demo_*` data, so they must connect to a dedicated test database. The framework's real MySQL tests use another set of environment variables: `DEC_MYSQL_JDBC_URL`, `DEC_MYSQL_USER`, `DEC_MYSQL_PASSWORD`; once configured, run:

```sh
./dec-java-lite/mvnw -f dec-java-lite/pom.xml -Pmysql-it verify
```

Without a database environment, some conditional tests may be skipped; only an actual run report can prove that the real MySQL path has been verified. Do not attribute local in-memory test results to the database driver.

### What concurrency tests should prove

Concurrency regression tests must create race windows deterministically and check the persisted results after failure. `concurrentHttpSubmissionsReturnOneSuccessAndOneConflictWithoutPartialWrites` in [OrderApiTest](../../demo/order-payment-app/src/test/java/com/example/orderpayment/demo/OrderApiTest.java) uses a barrier so that both requests read "order does not exist" before proceeding, avoiding accidentally testing only sequential duplicate requests.

The tests cover the same new user, the same existing user, and different new users contending for the same order ID, asserting in each case one success and one conflict in HTTP status (200/409), and that in the end only one order, one detail, and one user exist; the different-new-users scenario also verifies that user initialization in the failed request is rolled back. This combination of "race window + API result + data integrity" says more about transaction behavior than merely asserting that an exception is thrown.

When changing conflict mapping, also distinguish business duplicates from infrastructure failures: a database primary-key conflict may return 409 per the business contract, but network or commit failures must not be turned into 409 across the board. The tests depend on real MySQL, with execution conditions and database cleanup requirements as described in this section.

## 6. Use Binding to state who implements what

Basic check:

```sh
python3 skills/dec-implementation/scripts/validate_binding.py \
  --design demo/order-payment-yaml \
  --binding demo/order-payment-implementation-binding.yaml \
  --project-root demo/order-payment-app \
  --require-complete --require-tests --require-symbols
```

This checks design coverage, test declarations, and the existence of Java declarations, but it does not mean those tests have just passed. Test evidence should be generated by the wrapper after it runs the real tests:

```sh
python3 skills/dec-implementation/scripts/run_binding_verification.py \
  --design demo/order-payment-yaml \
  --binding demo/order-payment-implementation-binding.yaml \
  --project-root demo/order-payment-app \
  --input ../../dec-java-lite \
  --input ../../skills/dec-implementation \
  --input ../order-payment-generation.properties \
  -- ../../dec-java-lite/mvnw -q clean test

python3 skills/dec-implementation/scripts/validate_binding.py \
  --design demo/order-payment-yaml \
  --binding demo/order-payment-implementation-binding.yaml \
  --project-root demo/order-payment-app \
  --require-complete --require-evidence
```

The wrapper's test command and `--input` are resolved relative to `project-root`; install the framework dependencies matching this source first. The last extra input contains the generation configuration; even if the default grouping is currently used, that project configuration file can also be brought into change tracking.

The default evidence is the application's `target/dec-binding-evidence.json`, which links the design, Binding, source code, extra dependencies, and the JUnit XML summary. The report must come from this run; failures/skips do not count as passing evidence. Java symbol checking uses the AST; method overloads require an explicit signature.

| Flag | What it proves |
| --- | --- |
| `--require-complete` | Every Design ID has a disposition; PLANNED/BLOCKED allowed |
| `--require-tests` | Implemented/verified nodes declare tests |
| `--require-symbols` | The real Java declarations exist in the specified files and are unambiguous |
| `--require-evidence` | Declared tests have a passing report from this run that matches the current inputs |
| `--require-ready` | Complete, symbols, and evidence all qualify, and everything is VERIFIED |

See the [integration notes](../../demo/INTEGRATION.md) for the demo's current status and unimplemented items; as long as any PLANNED remains, the release readiness check should fail. Do not batch-edit statuses to make the failure go away. The digest is a drift check, not a security signature, and it does not prove the assertions are necessarily sufficient. The Java semanticDigest differs from the Binding design digest; see the [Binding contract](../../skills/dec-implementation/references/implementation-binding.md) for details.

## 7. Check derived artifacts for drift

Regenerate into a temporary directory instead of modifying managed files to align them:

```sh
dec_doc_check_dir=$(mktemp -d)
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml \
  -o "$dec_doc_check_dir/xml" --emit-id-comments
python3 skills/dec-design/scripts/render_dec_graph.py demo/order-payment-yaml \
  -o "$dec_doc_check_dir/html"
diff -qr --exclude=.DS_Store demo/order-payment-xml "$dec_doc_check_dir/xml"
diff -qr --exclude=.DS_Store demo/order-payment-app/src/main/resources/dec/design "$dec_doc_check_dir/xml"
diff -qr --exclude=.DS_Store demo/design-html "$dec_doc_check_dir/html"
```

Java's generated snapshots are checked by the generator tests; handwritten files in the application must not be bulk-compared against a purely generated output directory and then deleted.

After Skill files change, check the package manifests; the available options:

```sh
python3 skills/dec-design/scripts/check_manifest.py --help
python3 skills/dec-implementation/scripts/check_manifest.py
```

`dec-design` and `dec-implementation` each maintain their own source manifest; after changing a Skill, run the corresponding validation script first, then run that Skill's Python tests.

## 8. When a design change counts as complete

1. The canonical design is unambiguous, its IDs are stable, and full directory validation passes.
2. The XML export, application mirror, HTML, and Java managed artifacts agree with the design.
3. Handwritten adaptation has adjusted to the contract changes; no new business logic has been duplicated.
4. Related success, failure, and boundary tests actually pass, and database paths state whether they ran.
5. Binding symbols and evidence correspond to the current version; unimplemented items keep their true status.
6. The application rebuilds the Runtime from the newly packaged design; it is not just refreshing the HTML in the browser.

## 9. Where to look first when something goes wrong

| Problem | Check first |
| --- | --- |
| HTML and code disagree in meaning | Whether only derived artifacts were edited; whether the full site and the application mirror were regenerated |
| YAML unchanged after editing the XML | Sync is not triggered automatically; whether a write back was run rather than only `--check` |
| Compilation fails after generation | Whether the local framework was installed; whether handwritten ServiceImpls match the new interfaces |
| Rule returns UNRESOLVED/ERROR | Whether model assembly, RuleView registration, required relations, and data sources are all in place |
| Action/listener not executed | Whether the Business ID/Action name is registered exactly; whether the path actually reaches that action |
| Data not committed or outcome unknown | Transaction mode, resource owner, completion callback, and database commit exceptions |
| Query missing data / candidate cap hit | Whether current facts hold, whether relations are assembled, whether the bounded candidate scan limit was reached |
| Binding evidence invalidated | Whether the design, source, configuration, report, or dependencies changed, and whether tests need to be rerun |

The framework itself has detailed topic documents and scenario tests; continue the investigation from the [framework README](../../dec-java-lite/README.md).
