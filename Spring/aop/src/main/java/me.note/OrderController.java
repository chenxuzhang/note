package me.note;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/order")
public class OrderController {


    @Resource
    private OrderService orderService;

    @GetMapping("/create")
    public String create(Object object) {
        orderService.create(object);
        return "ok";
    }

}
