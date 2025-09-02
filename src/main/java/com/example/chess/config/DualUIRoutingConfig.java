package com.example.chess.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Configuration for dual UI support - existing UI at root and new React UI at /newage/chess
 * This ensures zero disruption to existing users while providing the new React interface
 */
@Configuration
public class DualUIRoutingConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve existing UI at root path (unchanged)
        registry.addResourceHandler("/")
                .addResourceLocations("classpath:/templates/");
        
        // Serve React build files for new UI at /newage/chess/**
        registry.addResourceHandler("/newage/chess/**")
                .addResourceLocations("classpath:/static/newage/chess/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        return requestedResource.exists() && requestedResource.isReadable() 
                            ? requestedResource 
                            : new org.springframework.core.io.ClassPathResource("/static/newage/chess/index.html");
                    }
                });
        
        // Serve React static assets
        registry.addResourceHandler("/newage/chess/static/**")
                .addResourceLocations("classpath:/static/newage/chess/static/");
        
        // Serve React assets (JS, CSS, etc.)
        registry.addResourceHandler("/newage/chess/assets/**")
                .addResourceLocations("classpath:/static/newage/chess/assets/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Redirect /newage/chess to the React app
        registry.addViewController("/newage/chess")
                .setViewName("forward:/newage/chess/index.html");
        
        // Handle React Router routes - all /newage/chess/* routes should serve index.html
        registry.addViewController("/newage/chess/**")
                .setViewName("forward:/newage/chess/index.html");
    }
}
