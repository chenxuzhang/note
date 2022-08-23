package me.note;

import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService {

    @SysLog
    @Override
    public void create(Object object) {
        System.out.println("create order.....");
    }
}
