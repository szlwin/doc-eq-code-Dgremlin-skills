# dec-implementation

`dec-implementation` 是独立 DEC 实现 Skill：消费已批准 canonical YAML，把需要项目代码承载的 Design ID 实现到真实仓库，并维护 **Design ID → Runtime/Code → Test** Binding。

它不依赖任何研发流程 Skill。可以单独消费已有 DEC YAML，也可以与 `dec-design` 组合使用。

## 读取顺序

```text
SKILL.md
  ↓
references/dec-design-consumer-contract.md
  ↓
references/input-contract.md
  ↓
当前 kind 的 DEC YAML 规范 + canonical YAML
  ↓
references/implementation-workflow.md
  ↓
references/implementation-binding.md
```

按需：DataSource → `datasource-implementation.md`；Enum/relEnum → `enum-implementation.md`；API → `api-implementation.md`；Rule/Action → `rule-action-implementation.md`。

## Binding 校验

```sh
python3 -m pip install -r requirements.txt
python3 scripts/validate_binding.py \
  --design path/to/dec-yaml \
  --binding examples/implementation-binding.yaml

# 交付前要求所有 Design ID 都有绑定；非 Runtime 实现还要求测试
python3 scripts/validate_binding.py \
  --design path/to/dec-yaml \
  --binding implementation-binding.yaml \
  --require-complete --require-tests
```

实现不得重新解释/改写 DEC 业务语义；发现设计缺口返回 `DESIGN_GAP`。


## YAML / XML I/O

本 Skill 的 YAML/XML 访问必须经过脚本：

```sh
python3 scripts/yaml_io.py read path/to/dec-yaml
python3 scripts/yaml_io.py write implementation-binding.yaml --from-json /tmp/binding.json
python3 scripts/yaml_io.py merge implementation-binding.yaml --patch-json /tmp/binding-patch.json
python3 scripts/xml_io.py check path/to/generated-xml
python3 scripts/xml_io.py read path/to/generated.xml
```

禁止直接打开、拼接或手工修改 YAML/XML。`dec-implementation` 不写 DEC XML；需要重建 XML 时使用设计侧正式 YAML→XML converter。
