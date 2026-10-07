package com.example.orderpayment.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {
    @Autowired MockMvc mvc;

    @Test void generatedControllersReachTheExecutableWorkflow() throws Exception {
        mvc.perform(post("/api/orders/501/submit").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"1","type":"1","userId":1,"productId":7,
                         "productName":"Demo item","productPrice":14.95,"productCount":2,"remark":"demo-order"}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("1"))
                .andExpect(jsonPath("$.remark").value("demo-order"))
                .andExpect(jsonPath("$.type").value("1"));
        mvc.perform(post("/api/orders/501/payment"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("2"));
        mvc.perform(post("/api/orders/501/payment/result").contentType(MediaType.APPLICATION_JSON)
                .content("{\"resultCode\":\"ERROR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("4"));
        mvc.perform(get("/api/orders/501"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payInfo.resultCode").value("ERROR"));
        mvc.perform(post("/api/orders/501/payment/back"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("2"))
                .andExpect(jsonPath("$.payInfo.resultCode").doesNotExist());
    }

    @Test void rejectsInvalidTransportInputBeforeCreatingOrder() throws Exception {
        mvc.perform(post("/api/orders/502/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"2\",\"type\":\"1\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders/502")).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/503/submit").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"1","type":"1","userId":1,"productId":7,
                         "productName":"Demo item","productPrice":0.50,"productCount":2}
                        """))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders/503")).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/0/submit").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"1","type":"1","userId":1,"productId":7,
                         "productName":"Demo item","productPrice":14.95,"productCount":2}
                        """))
                .andExpect(status().isBadRequest());
    }
}
