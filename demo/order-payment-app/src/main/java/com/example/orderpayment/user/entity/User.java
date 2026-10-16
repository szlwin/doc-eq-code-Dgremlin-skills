package com.example.orderpayment.user.entity;


/** DEC DATA-USER; YAML data-order.yaml#datas[id=DATA-USER]; XML data-order.xml data[name=user]. */
public class User {
    /** YAML data-order.yaml#datas[id=DATA-USER].properties.id; XML data-order.xml property[name=id]. */
    private Integer id;
    /** YAML data-order.yaml#datas[id=DATA-USER].properties.name; XML data-order.xml property[name=name]. */
    private String name;
    /** YAML data-order.yaml#datas[id=DATA-USER].properties.active; XML data-order.xml property[name=active]. */
    private Integer active;
    /** YAML data-order.yaml#datas[id=DATA-USER].properties.certified; XML data-order.xml property[name=certified]. */
    private Integer certified;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
    }
    public Integer getCertified() {
        return certified;
    }

    public void setCertified(Integer certified) {
        this.certified = certified;
    }
}
