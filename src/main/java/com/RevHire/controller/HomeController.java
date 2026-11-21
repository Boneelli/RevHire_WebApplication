package com.RevHire.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.RevHire.entity.User;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Controller
public class HomeController {

    private static final Logger logger = LogManager.getLogger(HomeController.class);

    @GetMapping("/")
    public String index() {
        logger.info("Home page requested");
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        logger.info("Login page requested");
        return "auth/login";
    }

    @GetMapping("/register")
    public String showRegisterPage(final Model model) {
        logger.info("Register page requested");
        model.addAttribute("user", new User());
        return "auth/register";
    }
}