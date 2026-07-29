package com.elnourpower.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/** Sert la SPA statique (single page) sur la racine. */
@Controller
class PageController {
    @GetMapping("/")
    fun home(): String = "forward:/index.html"
}
