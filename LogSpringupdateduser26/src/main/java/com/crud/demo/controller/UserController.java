package com.crud.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.crud.demo.model.User;
import com.crud.demo.repository.UserRepository;
import com.crud.demo.service.UserService;

import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpSession;

@Controller
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HttpSession httpSession;

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }


    
    @PostMapping("/login")
    public String handleLogin(@RequestParam String identifier, @RequestParam String password, Model model) {
        Optional<User> userOptional = userService.loginWithUsernameOrEmail(identifier, password);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            httpSession.setAttribute("userId", user.getId());
            httpSession.setAttribute("userRole", user.getAccess());
            System.out.println("User access level set to: " + user.getAccess());
            return "redirect:/webpage";
        } else {
            model.addAttribute("error", "Invalid username/email or password");
            return "login";
        }
    }


    @GetMapping("/logout")
    public String handleLogout() {
        httpSession.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/settings")
    public String showSettingsPage(Model model) {
        Long userId = (Long) httpSession.getAttribute("userId");
        if (userId != null) {
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isPresent()) {
                model.addAttribute("user", userOptional.get());
            } else {
                model.addAttribute("error", "User not found.");
            }
        }
        return "settings";
    }

    @GetMapping("/settings/edit")
    public String editUserDetails(@RequestParam Long userId, Model model) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            model.addAttribute("user", userOptional.get());
            return "editUser"; // Assuming you have a separate edit page
        } else {
            model.addAttribute("error", "User not found.");
            return "redirect:/settings";
        }
    }

    @PostMapping("/Logging_cred")
    public String handleUserDetailsSubmission(@ModelAttribute User user, RedirectAttributes redirectAttributes) {
        user.setEnabled(true); // Enable user by default

        // Validate required fields
        if (isUserDetailsInvalid(user)) {
            redirectAttributes.addFlashAttribute("error", "All fields are required.");
            return "redirect:/allusers";  // Redirect back to all users page
        }

        // Check if the user has an ID for update, else create a new user
        if (user.getId() != null) {
            return updateUser(user, redirectAttributes);
        } else {
            return createUser(user, redirectAttributes); // Handle new user creation
        }
    }

    private boolean isUserDetailsInvalid(User user) {
        return user.getFirstName() == null || user.getFirstName().isEmpty() ||
               user.getLastName() == null || user.getLastName().isEmpty() || 
               user.getDateOfBirth() == null ||  
               user.getGender() == null || user.getGender().isEmpty() || 
               user.getEmail() == null || user.getEmail().isEmpty() || 
               user.getMobileNumber() == null || user.getMobileNumber().isEmpty() || 
               user.getAccess() == null || user.getAccess().isEmpty()  || 
               user.getUsername() == null || user.getUsername().isEmpty() || 
               user.getPassword() == null || user.getPassword().isEmpty();
    }

    private String updateUser(User user, RedirectAttributes redirectAttributes) {
        Optional<User> userOptional = userRepository.findById(user.getId());
        if (userOptional.isPresent()) {
            User existingUser = userOptional.get();
            existingUser.setFirstName(user.getFirstName());
            existingUser.setMiddleName(user.getMiddleName());
            existingUser.setLastName(user.getLastName());
            existingUser.setDateOfBirth(user.getDateOfBirth());
            existingUser.setGender(user.getGender());
            existingUser.setEmail(user.getEmail());
            existingUser.setMobileNumber(user.getMobileNumber());
            existingUser.setAccess(user.getAccess());
            existingUser.setUsername(user.getUsername());
            existingUser.setPassword(user.getPassword());

            userRepository.save(existingUser);
            redirectAttributes.addFlashAttribute("message", "User details updated successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "User not found.");
        }
        return "redirect:/allusers"; 
    }

    private String createUser(User user, RedirectAttributes redirectAttributes) {
        try {
            if (userRepository.findByUsername(user.getUsername()).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Username already exists.");
                return "redirect:/allusers";  // Redirect back to the form if the username is already taken
            }

            userService.registerUser(user); // Use UserService to register the new user
            redirectAttributes.addFlashAttribute("message", "User added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error adding user: " + e.getMessage());
        }
        return "redirect:/allusers";  // After successful addition, redirect to all users page
    }

    @PostMapping("/allusers")
    public String searchOrCreateUser(@RequestParam(required = false) String userSearch, 
                                     @ModelAttribute("user") User user, 
                                     Model model, RedirectAttributes redirectAttributes) {
        Long userId = (Long) httpSession.getAttribute("userId");
        Optional<User> adminOptional = userRepository.findById(userId);

        if (adminOptional.isPresent() && "admin".equals(adminOptional.get().getAccess())) {
            if (userSearch != null && !userSearch.isEmpty()) {
                // Search user by criteria
                List<User> users = userService.findUsersByCriteria(userSearch);
                model.addAttribute("users", users);
                
                if (!users.isEmpty()) {
                    model.addAttribute("selectedUser", users.get(0)); 
                } else {
                    model.addAttribute("error", "No users found.");
                }
                return "allusers";
            } else {
                // Attempt to create a new user if no search term is provided
                if (userRepository.findByUsername(user.getUsername()).isPresent()) {
                    redirectAttributes.addFlashAttribute("error", "User already exists with username: " + user.getUsername());
                } else {
                    userService.registerUser(user); // Register user if username does not exist
                    redirectAttributes.addFlashAttribute("message", "User added successfully!");
                }
                return "redirect:/allusers";  // After adding, redirect to all users page
            }
        } else {
            model.addAttribute("errorMessage", "You do not have permission to view all users.");
            return "settings";
        }
    }

    @PostMapping("/registerUser")
    public String registerUser(@ModelAttribute User user, RedirectAttributes redirectAttributes) {
        user.setEnabled(true); // Enable user by default
        String message = userService.registerUser(user); // Register user using UserService

        if (message.equals("Registration successful")) {
            redirectAttributes.addFlashAttribute("message", "User registered successfully!");
            return "redirect:/login"; 
        } else {
            redirectAttributes.addFlashAttribute("error", message);
            return "redirect:/registrationlogin"; 
        }
    }

    @PostMapping("/registrationlogin")
    public String handleLogin1(@RequestParam String username, Model model) {
        Optional<User> userOptional = userRepository.findByUsernameAndEnabled(username, true);
        
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            httpSession.setAttribute("userId", user.getId());
            httpSession.setAttribute("userRole", user.getAccess()); // Store access level in session
            return "redirect:/webpage";
        } else {
            model.addAttribute("error", "User account is disabled.");
            return "registrationlogin"; 
        }
    }

}
