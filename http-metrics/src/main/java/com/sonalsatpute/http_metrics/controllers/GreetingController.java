package com.sonalsatpute.http_metrics.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    @GetMapping("/greeting/{name}")
    public String greeting(
            @PathVariable(required = false) String name,
            @RequestParam(defaultValue = "10") short pageSize,
            @RequestParam(defaultValue = "1") short pageIndex) {

        System.out.println("Name: " + name);
        System.out.println("PageSize: " + pageSize);
        System.out.println("PageIndex: " + pageIndex);

        try {
            System.out.println("Sleeping for 5 seconds...");
            Thread.sleep(5000);
            System.out.println("Awake!");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (name != null && !name.isEmpty()) {
            return "Hello, " + name + "!";
        }

        return "Hello, World!";
    }
}
