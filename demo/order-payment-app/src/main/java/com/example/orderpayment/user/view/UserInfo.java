package com.example.orderpayment.user.view;


/** DEC VIEW-USER-INFO; YAML view-order.yaml#views[id=VIEW-USER-INFO]; XML view-order.xml view[name=UserInfo]. */
public class UserInfo {
    /** YAML view-order.yaml#views[id=VIEW-USER-INFO].properties.id; XML view-order.xml property[name=id]. */
    private Integer id;
    /** YAML view-order.yaml#views[id=VIEW-USER-INFO].properties.name; XML view-order.xml property[name=name]. */
    private String name;
    /** YAML view-order.yaml#views[id=VIEW-USER-INFO].properties.active; XML view-order.xml property[name=active]. */
    private Integer active;
    /** YAML view-order.yaml#views[id=VIEW-USER-INFO].properties.certified; XML view-order.xml property[name=certified]. */
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
