package com.crud.demo.controller;

import com.crud.demo.model.StatisticsFinal;

import com.crud.demo.service.StatisticsFinalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class FileUploadController {

    private List<String> allLogs;
    private List<String> detailedErrorLogs;
    private List<String> downloadedFilenames = new ArrayList<>();

    @Autowired
    private StatisticsFinalService statisticsFinalService;

    @Autowired
    private HttpSession httpSession;

    @GetMapping("/")
    public String index() {
        return "login";
    }
    @GetMapping("/chat")
    public String chat(Model model) {
        return "chat";
    }
    @GetMapping("/registration")
    public String registration(Model model) {
        return "registration";
    }
    @GetMapping("/registrationlogin")
    public String registrationlogin(Model model) {
        return "registrationlogin";
    }
    @GetMapping("/webpage")
    public String webpage(Model model) {
        return "webpage";
    }
    
    @GetMapping("/allusers")
    public String allusers(Model model) {
    	return "allusers";
    }
    
    @GetMapping("/addusers")
    public String addusers(Model model) {
    	return "addusers";
    }
   

    @GetMapping("/file")
    public String upload(Model model) {
        // Add the uploaded logs and counts to the model for display
        if (allLogs != null && detailedErrorLogs != null) {
            model.addAttribute("uploadedFileName", httpSession.getAttribute("uploadedFileName"));
            model.addAttribute("counts", httpSession.getAttribute("logCounts"));
            model.addAttribute("allLogs", allLogs);
            model.addAttribute("detailedErrorLogs", detailedErrorLogs);
        }
        return "file";
    }

    @PostMapping("/upload")
    public String uploadLogFile(@RequestParam("logfile") MultipartFile logFile, Model model) {
        Long userId = getCurrentUserId();

        if (logFile.isEmpty()) {
            model.addAttribute("error", "Please select a file to upload.");
            return "webpage";
        }
        

        String uploadedFileName = logFile.getOriginalFilename();
        StringBuilder downloadedExceptions = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(logFile.getInputStream()))) {
            List<String> logLines = reader.lines().collect(Collectors.toList());

            Map<String, Integer> counts = countLogOccurrences(logLines);
            Map<String, Integer> filteredCounts = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            allLogs = logLines;
            detailedErrorLogs = extractDetailedErrorLogs(logLines);

            // Add data to the model and save in session
            httpSession.setAttribute("uploadedFileName", uploadedFileName);
            httpSession.setAttribute("logCounts", filteredCounts);

            model.addAttribute("uploadedFileName", uploadedFileName);
            model.addAttribute("counts", filteredCounts);
            model.addAttribute("allLogs", allLogs);
            model.addAttribute("detailedErrorLogs", detailedErrorLogs);

            // Capture the exceptions downloaded
            Set<String> uniqueDownloadedExceptions = new HashSet<>();
            for (String exceptionType : filteredCounts.keySet()) {
                if (filteredCounts.get(exceptionType) > 0) {
                    uniqueDownloadedExceptions.add(exceptionType);
                }
            }

            String downloadedExceptionsStr = String.join(", ", uniqueDownloadedExceptions);
            
            Set<String> uniqueResultingfilenames = new HashSet<>();
            for (String resultingfilenames : filteredCounts.keySet()) {
                if (filteredCounts.get(resultingfilenames) > 0) {
                	uniqueResultingfilenames.add(resultingfilenames);
                }
            }

            String resultingfilenamesStr = String.join(", ", uniqueResultingfilenames);
            
            String resultingFileName = generateResultingFileName(uploadedFileName, "Statistics");
           
            // Save statistics with the downloaded exceptions captured
            statisticsFinalService.saveStatistics(
                userId,
                uploadedFileName,
                null,
                filteredCounts.getOrDefault("AccessException", 0),
                filteredCounts.getOrDefault("CloudClientException", 0),
                filteredCounts.getOrDefault("InvalidFormatException", 0),
                filteredCounts.getOrDefault("NullPointerException", 0),               
                filteredCounts.getOrDefault("SchedulerException", 0),
                filteredCounts.getOrDefault("SuperCsvException", 0),
                filteredCounts.getOrDefault("ValidationException", 0),
                filteredCounts.getOrDefault("ERROR", 0),
                filteredCounts.getOrDefault("INFO", 0),
                filteredCounts.getOrDefault("DEBUG", 0),
                filteredCounts.keySet().toString(),
                "Uploaded",
                downloadedExceptions.toString()
                
            );

        } catch (IOException e) {
            model.addAttribute("error", "Failed to process the file: " + e.getMessage());
        }

        return "webpage"; // Redirect to /file to display the uploaded details
    }


    private Long getCurrentUserId() {
        return (Long) httpSession.getAttribute("userId");
    }

    private Map<String, Integer> countLogOccurrences(List<String> logLines) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("ERROR", countOccurrences(logLines, "ERROR"));
        counts.put("INFO", countOccurrences(logLines, "INFO"));
        counts.put("DEBUG", countOccurrences(logLines, "DEBUG"));
        counts.put("NullPointerException", countOccurrences(logLines, "NullPointerException"));
        counts.put("ValidationException", countOccurrences(logLines, "ValidationException"));
        counts.put("SchedulerException", countOccurrences(logLines, "SchedulerException"));
        counts.put("AccessException", countOccurrences(logLines, "AccessException"));
        counts.put("InvalidFormatException", countOccurrences(logLines, "InvalidFormatException"));
        counts.put("CloudClientException", countOccurrences(logLines, "CloudClientException"));        
        counts.put("SuperCsvException", countOccurrences(logLines, "SuperCsvException"));  
        return counts;
    }

    @GetMapping("/downloadErrorLogs")
    public ResponseEntity<InputStreamResource> downloadLogs(Model model) throws IOException {
        if (detailedErrorLogs == null) {
            return ResponseEntity.badRequest().body(null);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Detailed Error Logs:\n").append(String.join("\n", detailedErrorLogs)).append("\n\n");

        ByteArrayInputStream in = new ByteArrayInputStream(sb.toString().getBytes());
        InputStreamResource resource = new InputStreamResource(in);

        String resultingFileName = generateResultingFileName("DetailedErrorLogs", "");

        // Append the filename to the session        
        // Save the resulting file name
        saveResultingFileName(resultingFileName, "Downloaded");
        saveDownloadedException(resultingFileName, "Detailed Error Logs");

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + resultingFileName)
            .contentType(MediaType.TEXT_PLAIN)
            .body(resource);
    }


    @GetMapping("/downloadFilteredErrorLogs")
    public ResponseEntity<InputStreamResource> downloadFilteredLogs(@RequestParam("exceptionType") String exceptionType) throws IOException {
        if (detailedErrorLogs == null || exceptionType == null || exceptionType.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        List<String> filteredLogs = detailedErrorLogs.stream()
            .filter(stackTrace -> stackTrace.contains(exceptionType))
            .collect(Collectors.toList());
        List<String> downloadedFilenames = (List<String>) httpSession.getAttribute("downloadedFilenames");
        if (downloadedFilenames == null) {
            downloadedFilenames = new ArrayList<>();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.join("\n", filteredLogs)).append("\n\n\n");

        ByteArrayInputStream in = new ByteArrayInputStream(sb.toString().getBytes());
        InputStreamResource resource = new InputStreamResource(in);

        // Generate the resulting file name with the respective exception name
        String resultingFileName = generateResultingFileName(exceptionType, "FilteredLogs");
        // Store the downloaded filename
        downloadedFilenames.add(resultingFileName);
        httpSession.setAttribute("downloadedFilenames", downloadedFilenames);
        // Save the resulting file name to the database
        saveResultingFileName(resultingFileName, "Downloaded");
        // Save the resulting file name and exception type to the downloadedException field in the database
        saveDownloadedException(resultingFileName, exceptionType);

        // Set the file name dynamically in the response header
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + resultingFileName)
            .contentType(MediaType.TEXT_PLAIN)
            .body(resource);
    }

    @GetMapping("/filteredErrorLogs")
    @ResponseBody
    public List<String> filteredErrorLogs(@RequestParam("exceptionType") String exceptionType) {
        if (detailedErrorLogs == null || exceptionType == null || exceptionType.isEmpty()) {
            return Collections.emptyList();
        }

        return detailedErrorLogs.stream()
            .filter(stackTrace -> stackTrace.contains(exceptionType))
            .collect(Collectors.toList());
    }

    private int countOccurrences(List<String> logLines, String keyword) {
        return (int) logLines.stream().filter(line -> line.contains(keyword)).count();
    }

    private List<String> extractDetailedErrorLogs(List<String> logLines) {
        List<String> detailedLogs = new ArrayList<>();
        boolean isCapturing = false;  // Tracks whether we are capturing a log entry
        StringBuilder currentStackTrace = new StringBuilder();  // Holds the current stack trace
        String currentLogType = "";  // Tracks the current log type being captured (ERROR, INFO, DEBUG)

        for (String line : logLines) {
            if (line.contains("ERROR")) {
                if (!currentLogType.equals("ERROR")) {
                    if (isCapturing) {
                        // If we were already capturing, add the previous log entry before starting a new one
                        detailedLogs.add(currentStackTrace.toString().trim());
                    }
                    // Start capturing a new ERROR entry
                    isCapturing = true;
                    currentStackTrace = new StringBuilder();  
                    currentStackTrace.append(line).append("\n");
                    currentLogType = "ERROR";  // Update the log type
                } else if (isCapturing) {
                    // Continue capturing ERROR log
                    currentStackTrace.append(line).append("\n");
                }
            } else if (line.contains("INFO")) {
                if (!currentLogType.equals("INFO")) {
                    if (isCapturing) {
                        detailedLogs.add(currentStackTrace.toString().trim());
                    }
                    isCapturing = true;
                    currentStackTrace = new StringBuilder();  
                    currentStackTrace.append(line).append("\n");
                    currentLogType = "INFO";  
                } else if (isCapturing) {
                    currentStackTrace.append(line).append("\n");
                }
            } else if (line.contains("DEBUG")) {
                if (!currentLogType.equals("DEBUG")) {
                    if (isCapturing) {
                        detailedLogs.add(currentStackTrace.toString().trim());
                    }
                    isCapturing = true;
                    currentStackTrace = new StringBuilder();  
                    currentStackTrace.append(line).append("\n");
                    currentLogType = "DEBUG";
                } else if (isCapturing) {
                    currentStackTrace.append(line).append("\n");
                }
            } else if (isCapturing) {
                if (line.isEmpty() || line.startsWith("ERROR") || line.startsWith("INFO") || line.startsWith("DEBUG")) {
                    // End the current stack trace when a new log type or empty line is encountered
                    detailedLogs.add(currentStackTrace.toString().trim());
                    isCapturing = false;  // Reset capturing flag
                } else {
                    // Continue adding lines to the current stack trace
                    currentStackTrace.append(line).append("\n");
                }
            }
        }
        // If we were still capturing at the end of the loop, add the last captured log
        if (isCapturing) {
            detailedLogs.add(currentStackTrace.toString().trim());
        }

        return detailedLogs;
    }

    private String generateResultingFileName(String baseName, String suffix) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);
        return baseName + "_" + timestamp + (suffix.isEmpty() ? "" : "_" + suffix) + ".txt";
    }

    private void saveResultingFileName(String filename, String status) {
        Long userId = getCurrentUserId();
        // Retrieve the existing list of filenames from the session
        List<String> resultingFileNames = (List<String>) httpSession.getAttribute("resultingFileNames");
        if (resultingFileNames == null) {
            resultingFileNames = new ArrayList<>();
        }

        // Add the new filename to the list
        resultingFileNames.add(filename);

        // Save the updated list of filenames back to the session
        httpSession.setAttribute("resultingFileNames", resultingFileNames);

        // Update the statistics with the resulting file name and status
        statisticsFinalService.updateResultingFileName(userId, filename, status);
    }
    private void saveDownloadedException(String filename, String exceptionType) {
        Long userId = getCurrentUserId();
        // Retrieve the existing list of downloaded filenames from the session
        List<String> downloadedFilenames = (List<String>) httpSession.getAttribute("downloadedFilenames");
        if (downloadedFilenames == null) {
            downloadedFilenames = new ArrayList<>();
        }

        // Add the new filename to the list
        downloadedFilenames.add(filename);

        // Save the updated list of downloaded filenames back to the session
        httpSession.setAttribute("downloadedFilenames", downloadedFilenames);

        // Update the statistics with the resulting file name and the exception type as "Downloaded Exception"
        statisticsFinalService.updateDownloadedException(userId, filename, exceptionType);
    }


    @GetMapping("/statistics")
    public String statistics(Model model) {
        Long userId = getCurrentUserId();
        List<StatisticsFinal> statisticsList = statisticsFinalService.getStatisticsByUserId(userId);
        model.addAttribute("statistics", statisticsList);

        // Retrieve and display all resulting file names
        List<String> resultingFileNames = (List<String>) httpSession.getAttribute("resultingFileNames");
        model.addAttribute("resultingFileNames", resultingFileNames != null ? resultingFileNames : Collections.emptyList());

        List<String> downloadedFilenames = (List<String>) httpSession.getAttribute("downloadedFilenames");
        model.addAttribute("downloadedFilenames", downloadedFilenames != null ? downloadedFilenames : Collections.emptyList());

        return "statistics";
    }
    @GetMapping("/api/statistics")
    @ResponseBody
    public List<StatisticsFinal> getFilteredStatistics(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        
        Long userId = getCurrentUserId(); // Assuming this gets the current user ID
        
        LocalDateTime fromDate = null;
        LocalDateTime toDate = null;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        if (from != null && !from.isEmpty()) {
            fromDate = LocalDate.parse(from, formatter).atStartOfDay();
        }

        if (to != null && !to.isEmpty()) {
            toDate = LocalDate.parse(to, formatter).atTime(23, 59, 59); 
        }

        if (fromDate != null && toDate != null) {
            return statisticsFinalService.getStatisticsByDateRange(userId, fromDate, toDate);
        }

        return statisticsFinalService.getStatisticsByUserId(userId); // If no date provided, return all data
    } 
    @GetMapping("/filteredlogs")
    public String filteredLogs(@RequestParam("accessType") String accessType, 
                               @RequestParam("count") int count,
                               Model model) {
        List<String> filteredLogs = filteredErrorLogs(accessType);
        model.addAttribute("filteredLogs", filteredLogs);
        model.addAttribute("exceptionType", accessType); // Send the exception type for display
        return "filteredlogs"; // Ensure this is the correct template name
    }
}
