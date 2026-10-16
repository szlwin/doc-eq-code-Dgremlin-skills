package com.example.order.order.view;


/** DEC OrderInfo.user; YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.user; XML view-order.xml view[name=OrderInfo]. */
public class OrderInfoUser {
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.user.properties.id; XML view-order.xml property[name=id]. */
    private Integer id;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.user.properties.name; XML view-order.xml property[name=name]. */
    private String name;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.user.properties.active; XML view-order.xml property[name=active]. */
    private Integer active;
    /** YAML view-order.yaml#views[id=VIEW-ORDER-INFO].properties.user.properties.certified; XML view-order.xml property[name=certified]. */
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
