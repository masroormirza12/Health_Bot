package com.example.prescriptionbot.service;

import com.example.prescriptionbot.dto.BloodTestExtractionDTO;
import com.example.prescriptionbot.dto.MedicineDTO;
import com.example.prescriptionbot.dto.PendingPrescription;
import com.example.prescriptionbot.dto.PrescriptionDataDTO;
import com.example.prescriptionbot.dto.QueryIntentDTO;
import com.example.prescriptionbot.entity.*;
import com.example.prescriptionbot.repository.AttachmentRepository;
import com.example.prescriptionbot.repository.BloodTestResultRepository;
import com.example.prescriptionbot.repository.PersonRepository;
import com.example.prescriptionbot.repository.PrescriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class PrescriptionService {

    private static final Logger log = LoggerFactory.getLogger(PrescriptionService.class);

    @Autowired
    private UserService userService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private AIService aiService;

    @Autowired
    private BloodTestResultRepository bloodTestResultRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private S3Service s3Service;

    // ── Image Processing ──

    /**
     * Process an image asynchronously.
     * NOTE: This still currently blocks to return the result for the confirmation
     * flow.
     * In a future version, this could return a Future and notify the user via a
     * callback.
     */
    public PendingPrescription processImage(byte[] imageBytes, String contentType) {
        log.info("Starting async image processing (S3 upload + AI extraction)");

        CompletableFuture<String> s3Future = CompletableFuture
                .supplyAsync(() -> s3Service.uploadPrescription(imageBytes, contentType));

        CompletableFuture<PrescriptionDataDTO> aiFuture = CompletableFuture
                .supplyAsync(() -> aiService.extractPrescription(imageBytes));

        // FOR NOW: We wait because the bot flow depends on the immediate result to ask
        // the user for confirmation.
        // To make this truly non-blocking, we'd need to store the session and respond
        // later.
        CompletableFuture.allOf(s3Future, aiFuture).join();

        String s3Key = s3Future.join();
        PrescriptionDataDTO data = aiFuture.join();

        log.info("Image processing complete. S3 key={}, patientName={}", s3Key, data.getPatientName());

        return PendingPrescription.builder().data(data).s3Key(s3Key).build();
    }

    // ── Save Prescription ──

    @Transactional
    public Prescription confirmAndSavePrescription(String platformUserId, String username, RelationType relationType,
            PendingPrescription pending) {
        log.info("Confirming prescription for platformUserId={}, relationType={}", platformUserId, relationType);

        User user = userService.getOrCreateUser(platformUserId, username);

        PrescriptionDataDTO data = pending.getData();

        String personName = data.getPatientName() != null
                && !data.getPatientName().equalsIgnoreCase("UNKNOWN")
                && !data.getPatientName().equalsIgnoreCase("DEMO")
                        ? data.getPatientName()
                        : relationType.name();

        Person person = personRepository.findByUserAndRelationType(user, relationType)
                .orElseGet(() -> {
                    log.info("Creating new Person: name={}, relationType={}", personName, relationType);
                    Person newPerson = Person.builder()
                            .user(user)
                            .name(personName)
                            .relationType(relationType)
                            .build();
                    return personRepository.save(newPerson);
                });

        LocalDate visitDate;
        try {
            visitDate = data.getDate() != null && !data.getDate().equalsIgnoreCase("DEMO")
                    && !data.getDate().equalsIgnoreCase("UNKNOWN")
                            ? LocalDate.parse(data.getDate())
                            : LocalDate.now();
        } catch (DateTimeParseException e) {
            log.warn("Could not parse visitDate '{}', defaulting to today", data.getDate());
            visitDate = LocalDate.now();
        }

        Prescription prescription;
        if (pending.getExistingPrescriptionId() != null) {
            prescription = prescriptionRepository
                    .findByIdAndUserTelegramId(pending.getExistingPrescriptionId(), platformUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
            prescription.setHospitalName(data.getHospitalName());
            prescription.setDoctorName(data.getDoctorName());
            prescription.setVisitDate(visitDate);
            prescription.setPerson(person);
            if (prescription.getMedicines() != null) {
                prescription.getMedicines().clear();
            }
        } else {
            prescription = Prescription.builder()
                    .hospitalName(data.getHospitalName())
                    .doctorName(data.getDoctorName())
                    .visitDate(visitDate)
                    .s3Key(pending.getS3Key())
                    .person(person)
                    .build();
        }

        if (data.getMedicines() != null) {
            for (MedicineDTO mDto : data.getMedicines()) {
                Medicine medicine = Medicine.builder()
                        .name(mDto.getName())
                        .dosage(mDto.getDosage())
                        .frequency(mDto.getFrequency())
                        .duration(mDto.getDuration())
                        .build();
                prescription.addMedicine(medicine);
            }
        }

        Prescription saved = prescriptionRepository.save(prescription);
        log.info("Prescription saved successfully with id={} for personId={}", saved.getId(), person.getId());
        return saved;
    }

    @Transactional
    public void processAttachment(Long prescriptionId, AttachmentType type,
            byte[] fileBytes, String fileName, String contentType) {
        log.info("Processing attachment of type {} for prescriptionId={}", type, prescriptionId);

        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));

        // 1. Upload to S3
        String s3Key = s3Service.uploadPrescription(fileBytes, contentType);

        // 2. Create Attachment Entity
        Attachment attachment = Attachment
                .builder()
                .prescription(prescription)
                .type(type)
                .s3Key(s3Key)
                .fileName(fileName)
                .build();

        attachment = attachmentRepository.save(attachment);
        log.info("Attachment persisted with id={}", attachment.getId());

        // 3. Process based on type
        switch (type) {
            case BLOOD_TEST -> {
                log.info("Extracting structured blood test results for attachmentId={}", attachment.getId());
                BloodTestExtractionDTO extraction = aiService.extractBloodTestResults(fileBytes, contentType);
                if (extraction != null && extraction.getResults() != null) {
                    for (BloodTestExtractionDTO.BloodTestRowDTO row : extraction.getResults()) {
                        BloodTestResult result = BloodTestResult.builder()
                                .attachment(attachment)
                                .testName(row.getTestName())
                                .resultValue(row.getResultValue())
                                .unit(row.getUnit())
                                .referenceRange(row.getReferenceRange())
                                .build();
                        bloodTestResultRepository.save(result);
                    }
                    log.info("Saved {} test results for attachmentId={}", extraction.getResults().size(),
                            attachment.getId());
                }
            }
            case DISCHARGE_SUMMARY -> {
                log.info("Embedding discharge summary for prescriptionId={}", prescriptionId);
                aiService.embedDischargeSummary(prescriptionId, fileBytes);
            }
            default -> log.info("No AI parsing required for type {}", type);
        }

        prescription.addAttachment(attachment);
        prescriptionRepository.save(prescription);
        log.info("Attachment saved with id={}", attachment.getId());
    }

    @Transactional(readOnly = true)
    public Prescription getPrescriptionByIdAndUserId(Long id, String platformUserId) {
        return prescriptionRepository.findByIdAndUserTelegramId(id, platformUserId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found or access denied"));
    }

    // ── Query Dispatcher ──

    @Transactional(readOnly = true)
    public String handleQuery(String platformUserId, String userQuery) {
        log.info("Handling query for platformUserId={}, query='{}'", platformUserId, userQuery);

        QueryIntentDTO intent = aiService.parseIntent(userQuery);
        log.info("Parsed intent: name={}, role={}, doctor={}, medicine={}, startDate={}, endDate={}",
                intent.getIntentName(), intent.getRole(), intent.getDoctorName(),
                intent.getMedicineName(), intent.getStartDate(), intent.getEndDate());

        RelationType role = parseRole(intent.getRole());
        String intentName = intent.getIntentName() != null ? intent.getIntentName() : "UNKNOWN";

        String response = switch (intentName) {
            case "GET_MEDICINES" -> handleGetMedicines(platformUserId, role, intent);
            case "SEARCH_MEDICINE" -> handleSearchMedicine(platformUserId, role, intent);
            case "GET_DOCTORS" -> handleGetDoctors(platformUserId);
            case "GET_HOSPITALS" -> handleGetHospitals(platformUserId);
            case "GET_PRESCRIPTION_IMAGE" -> handleGetPrescriptionImage(platformUserId, role);
            case "GET_FAMILY_SUMMARY" -> handleGetFamilySummary(platformUserId, role);
            case "GET_FAMILY_TREE" -> handleGetFamilyTree(platformUserId);
            case "GET_MEDICINE_TIMELINE" -> handleGetMedicineTimeline(platformUserId, role, intent);
            case "GET_HEALTH_INSIGHTS" -> handleGetHealthInsights(platformUserId, role);
            case "QUERY_TEST_RESULT" -> handleQueryTestResult(platformUserId, role, intent);
            default -> "I didn't quite understand that. Try asking 'Show my prescriptions' or uploading an image.";
        };

        if ((intentName.equals("GET_MEDICINES") || intentName.equals("SEARCH_MEDICINE") ||
                intentName.equals("GET_FAMILY_SUMMARY") || intentName.equals("GET_MEDICINE_TIMELINE") ||
                intentName.equals("GET_HEALTH_INSIGHTS"))
                && !response.startsWith("No prescriptions") && !response.startsWith("No medical records")
                && !response.startsWith("No medicines found")) {
            response += "\n\n✏️ To edit or update any details above, reply with EDIT followed by the ID (e.g., EDIT 12)";
        }

        return response;
    }

    // ── Intent Handlers ──

    private String handleGetMedicines(String platformUserId, RelationType role, QueryIntentDTO intent) {
        LocalDate start = parseDate(intent.getStartDate(), LocalDate.now().minusYears(100));
        LocalDate end = parseDate(intent.getEndDate(), LocalDate.now());
        String doctor = intent.getDoctorName() != null ? intent.getDoctorName() : "";

        List<Prescription> results = prescriptionRepository.findByFilters(platformUserId, role, doctor, start, end);
        log.info("GET_MEDICINES returned {} prescriptions", results.size());

        if (results.isEmpty()) {
            String roleLabel = role != null ? role.name().toLowerCase() : "you";
            return "No prescriptions found for " + roleLabel + ".";
        }

        StringBuilder sb = new StringBuilder("📋 *Prescriptions" +
                (role != null ? " for " + role.name().toLowerCase() : "") + ":*\n\n");
        for (Prescription p : results) {
            appendPrescriptionBlock(sb, p);
        }
        return sb.toString();
    }

    private String handleSearchMedicine(String platformUserId, RelationType role, QueryIntentDTO intent) {
        String medicineName = intent.getMedicineName();
        if (medicineName == null || medicineName.isBlank()) {
            return "Please specify a medicine name to search for.";
        }

        List<Prescription> results = prescriptionRepository.findByMedicineName(platformUserId, role, medicineName);
        log.info("SEARCH_MEDICINE for '{}' returned {} prescriptions", medicineName, results.size());

        if (results.isEmpty()) {
            return "💊 No prescriptions found containing \"" + medicineName + "\".";
        }

        StringBuilder sb = new StringBuilder("🔍 *Found \"" + medicineName + "\" in " +
                results.size() + " prescription(s):*\n\n");
        for (Prescription p : results) {
            appendPrescriptionBlock(sb, p);
        }
        return sb.toString();
    }

    private String handleGetDoctors(String platformUserId) {
        List<String> doctors = prescriptionRepository.findDistinctDoctors(platformUserId);
        log.info("GET_DOCTORS returned {} doctors", doctors.size());

        if (doctors.isEmpty()) {
            return "No doctor records found.";
        }

        StringBuilder sb = new StringBuilder("👨‍⚕️ *Doctors you've visited:*\n\n");
        for (int i = 0; i < doctors.size(); i++) {
            sb.append(i + 1).append(". ").append(doctors.get(i)).append("\n");
        }
        return sb.toString();
    }

    private String handleGetHospitals(String platformUserId) {
        List<String> hospitals = prescriptionRepository.findDistinctHospitals(platformUserId);
        log.info("GET_HOSPITALS returned {} hospitals", hospitals.size());

        if (hospitals.isEmpty()) {
            return "No hospital records found.";
        }

        StringBuilder sb = new StringBuilder("🏥 *Hospitals you've visited:*\n\n");
        for (int i = 0; i < hospitals.size(); i++) {
            sb.append(i + 1).append(". ").append(hospitals.get(i)).append("\n");
        }
        return sb.toString();
    }

    private String handleGetPrescriptionImage(String platformUserId, RelationType role) {
        List<Prescription> results = prescriptionRepository.findLatestByUser(platformUserId, role);
        log.info("GET_PRESCRIPTION_IMAGE returned {} prescriptions", results.size());

        if (results.isEmpty()) {
            return "No prescriptions found.";
        }

        Prescription latest = results.get(0);
        if (latest.getS3Key() == null || latest.getS3Key().isBlank()) {
            return "📸 The latest prescription does not have an image stored.";
        }

        String url = s3Service.generatePresignedUrl(latest.getS3Key());
        String patientName = latest.getPerson() != null ? latest.getPerson().getName() : "Unknown";
        return "📸 *Latest prescription image for " + patientName + ":*\n" +
                "📅 Date: " + latest.getVisitDate() + "\n" +
                "👨‍⚕️ Doctor: " + (latest.getDoctorName() != null ? latest.getDoctorName() : "N/A") + "\n" +
                "🔗 " + url;
    }

    private String handleGetFamilySummary(String platformUserId, RelationType role) {
        if (role != null) {
            return handleIndividualSummary(platformUserId, role);
        }
        return handleFullFamilySummary(platformUserId);
    }

    private String handleIndividualSummary(String platformUserId, RelationType role) {
        List<Prescription> prescriptions = prescriptionRepository.findByFilters(
                platformUserId, role, "", LocalDate.now().minusYears(100), LocalDate.now());
        log.info("Individual summary for role={} returned {} prescriptions", role, prescriptions.size());

        if (prescriptions.isEmpty()) {
            return "No medical records found for " + role.name().toLowerCase() + ".";
        }

        String personName = prescriptions.get(0).getPerson() != null
                ? prescriptions.get(0).getPerson().getName()
                : role.name().toLowerCase();

        StringBuilder sb = new StringBuilder("📋 *Medical Summary for " + personName +
                " (" + role.name().toLowerCase() + "):*\n\n");

        sb.append("📊 Total prescriptions: ").append(prescriptions.size()).append("\n");

        long totalMeds = prescriptions.stream().mapToLong(p -> p.getMedicines().size()).sum();
        sb.append("💊 Total medicines prescribed: ").append(totalMeds).append("\n");

        long uniqueDoctors = prescriptions.stream()
                .map(Prescription::getDoctorName)
                .filter(d -> d != null)
                .distinct()
                .count();
        sb.append("👨‍⚕️ Doctors visited: ").append(uniqueDoctors).append("\n");

        long uniqueHospitals = prescriptions.stream()
                .map(Prescription::getHospitalName)
                .filter(h -> h != null)
                .distinct()
                .count();
        sb.append("🏥 Hospitals visited: ").append(uniqueHospitals).append("\n");

        prescriptions.stream().map(Prescription::getVisitDate).min(LocalDate::compareTo)
                .ifPresent(d -> sb.append("📅 First visit: ").append(d).append("\n"));
        prescriptions.stream().map(Prescription::getVisitDate).max(LocalDate::compareTo)
                .ifPresent(d -> sb.append("📅 Last visit: ").append(d).append("\n"));

        sb.append("\n💊 *All medicines prescribed:*\n");
        prescriptions.forEach(p -> p.getMedicines().forEach(m -> sb.append("  - ").append(m.getName())
                .append(m.getDosage() != null ? " (" + m.getDosage() + ")" : "")
                .append(" — ").append(p.getVisitDate())
                .append(p.getDoctorName() != null ? " by Dr. " + p.getDoctorName() : "")
                .append("\n")));

        return sb.toString();
    }

    private String handleFullFamilySummary(String platformUserId) {
        List<Prescription> allPrescriptions = prescriptionRepository.findAllForFamily(platformUserId);
        log.info("GET_FAMILY_SUMMARY returned {} total prescriptions", allPrescriptions.size());

        if (allPrescriptions.isEmpty()) {
            return "No prescriptions found for any family member.";
        }

        Map<String, List<Prescription>> byPerson = allPrescriptions.stream()
                .collect(Collectors.groupingBy(p -> {
                    Person per = p.getPerson();
                    return per.getRelationType().name() + " (" + per.getName() + ")";
                }));

        StringBuilder sb = new StringBuilder("👨‍👩‍👧‍👦 *Family Medical Summary:*\n\n");
        for (Map.Entry<String, List<Prescription>> entry : byPerson.entrySet()) {
            sb.append("👤 *").append(entry.getKey()).append("* — ")
                    .append(entry.getValue().size()).append(" prescription(s)\n");

            long totalMedicines = entry.getValue().stream()
                    .mapToLong(p -> p.getMedicines().size())
                    .sum();
            sb.append("   💊 Total medicines: ").append(totalMedicines).append("\n");

            long docs = entry.getValue().stream()
                    .map(Prescription::getDoctorName)
                    .filter(d -> d != null)
                    .distinct()
                    .count();
            sb.append("   👨‍⚕️ Doctors: ").append(docs).append("\n");

            entry.getValue().stream()
                    .map(Prescription::getVisitDate)
                    .max(LocalDate::compareTo)
                    .ifPresent(d -> sb.append("   📅 Last visit: ").append(d).append("\n"));

            sb.append("\n");
        }
        return sb.toString();
    }

    private String handleGetFamilyTree(String platformUserId) {
        User user = userService.getOrCreateUser(platformUserId);
        List<Person> persons = personRepository.findByUser(user);
        log.info("GET_FAMILY_TREE returned {} persons for platformUserId={}", persons.size(), platformUserId);

        if (persons.isEmpty()) {
            return "🌳 No family members found in your records yet.\n" +
                    "Upload a prescription and assign it to start building your family tree!";
        }

        StringBuilder sb = new StringBuilder("🌳 *Your Family Tree:*\n\n");
        sb.append("👤 Account: ").append(user.getUsername() != null ? user.getUsername() : "User #" + platformUserId)
                .append("\n");
        sb.append("─────────────\n");

        for (Person person : persons) {
            String emoji = switch (person.getRelationType()) {
                case SELF -> "🙋";
                case MOTHER -> "👩";
                case FATHER -> "👨";
                case BROTHER -> "👦";
                case OTHER -> "👥";
            };

            sb.append(emoji).append(" *").append(person.getName()).append("*")
                    .append(" — ").append(person.getRelationType().name().toLowerCase()).append("\n");

            long prescCount = person.getPrescriptions() != null ? person.getPrescriptions().size() : 0;
            sb.append("   📋 ").append(prescCount).append(" prescription(s) on record\n\n");
        }

        return sb.toString();
    }

    private String handleGetHealthInsights(String platformUserId, RelationType role) {
        List<Prescription> prescriptions = prescriptionRepository.findByFilters(
                platformUserId, role, "", LocalDate.now().minusYears(100), LocalDate.now());
        log.info("GET_HEALTH_INSIGHTS for role={} returned {} prescriptions", role, prescriptions.size());

        if (prescriptions.isEmpty()) {
            String roleLabel = role != null ? role.name().toLowerCase() : "you";
            return "No medical records found for " + roleLabel + " to analyze.";
        }

        String personName = prescriptions.get(0).getPerson() != null
                ? prescriptions.get(0).getPerson().getName()
                : (role != null ? role.name().toLowerCase() : "Patient");

        StringBuilder medSummary = new StringBuilder();
        for (Prescription p : prescriptions) {
            medSummary.append("Visit on ").append(p.getVisitDate());
            if (p.getDoctorName() != null) {
                medSummary.append(" (Dr. ").append(p.getDoctorName()).append(")");
            }
            if (p.getHospitalName() != null) {
                medSummary.append(" at ").append(p.getHospitalName());
            }
            medSummary.append(":\n");
            for (Medicine m : p.getMedicines()) {
                medSummary.append("  - ").append(m.getName());
                if (m.getDosage() != null)
                    medSummary.append(", dosage: ").append(m.getDosage());
                if (m.getFrequency() != null)
                    medSummary.append(", frequency: ").append(m.getFrequency());
                if (m.getDuration() != null)
                    medSummary.append(", duration: ").append(m.getDuration());
                medSummary.append("\n");
            }
            medSummary.append("\n");
        }

        log.info("Sending {} prescriptions to AI for health analysis, personName={}", prescriptions.size(),
                personName);
        String aiInsights = aiService.analyzeHealth(personName, medSummary.toString());

        StringBuilder sb = new StringBuilder("🧠 *Health Insights for " + personName + ":*\n\n");
        sb.append(aiInsights);
        sb.append("\n\n─────────────\n");
        sb.append("⚕️ _This is AI-generated analysis and NOT a substitute for professional medical advice._");

        return sb.toString();
    }

    private String handleGetMedicineTimeline(String platformUserId, RelationType role, QueryIntentDTO intent) {
        LocalDate start = parseDate(intent.getStartDate(), LocalDate.now().minusYears(1));
        LocalDate end = parseDate(intent.getEndDate(), LocalDate.now());

        List<Prescription> results = prescriptionRepository.findByFilters(
                platformUserId, role, "", start, end);
        log.info("GET_MEDICINE_TIMELINE returned {} prescriptions", results.size());

        if (results.isEmpty()) {
            String roleLabel = role != null ? role.name().toLowerCase() : "you";
            return "No medicines found for " + roleLabel + " in the specified period.";
        }

        String roleLabel = role != null ? role.name().toLowerCase() : "you";
        StringBuilder sb = new StringBuilder("📊 *Medicine timeline for " + roleLabel + ":*\n\n");

        results.stream()
                .sorted((a, b) -> a.getVisitDate().compareTo(b.getVisitDate()))
                .forEach(p -> {
                    sb.append("📅 ").append(p.getVisitDate());
                    if (p.getDoctorName() != null) {
                        sb.append(" (Dr. ").append(p.getDoctorName()).append(")");
                    }
                    sb.append("\n");
                    p.getMedicines().forEach(m -> sb.append("   💊 ").append(m.getName())
                            .append(m.getDosage() != null ? " — " + m.getDosage() : "")
                            .append(m.getDuration() != null ? " for " + m.getDuration() : "")
                            .append("\n"));
                    sb.append("\n");
                });
        return sb.toString();
    }

    private String handleQueryTestResult(String platformUserId, RelationType role, QueryIntentDTO intent) {
        String testName = intent.getTestName();
        if (testName == null || testName.isBlank()) {
            return "🔍 Which test result are you looking for? (e.g., 'What was my creatinine?')\n" +
                    "I can search through your uploaded blood tests.";
        }

        User user = userService.getOrCreateUser(platformUserId);
        Person person = personRepository.findByUserAndRelationType(user, role).orElse(null);
        if (person == null) {
            return "No medical records found for " + (role == RelationType.SELF ? "you" : role.name().toLowerCase())
                    + ".";
        }

        log.info("Searching for test '{}' for personId={}", testName, person.getId());

        List<Prescription> prescriptions = prescriptionRepository.findByPersonIdOrderByVisitDateDesc(person.getId());
        List<Attachment> bloodTests = prescriptions.stream()
                .flatMap(p -> p.getAttachments().stream())
                .filter(a -> a.getType() == AttachmentType.BLOOD_TEST)
                .toList();

        if (bloodTests.isEmpty()) {
            return "🩸 I don't see any blood test reports in the records for " + person.getName() + ".\n" +
                    "You can upload one by first sending a prescription image.";
        }

        StringBuilder sb = new StringBuilder("🩸 *Test Results for ").append(testName).append("*:\n\n");
        boolean found = false;

        for (Attachment at : bloodTests) {
            List<BloodTestResult> matches = bloodTestResultRepository.findByAttachmentId(at.getId()).stream()
                    .filter(r -> r.getTestName().toLowerCase().contains(testName.toLowerCase()))
                    .toList();

            if (!matches.isEmpty()) {
                found = true;
                sb.append("📅 Report Date: ").append(at.getPrescription().getVisitDate()).append("\n");
                for (BloodTestResult r : matches) {
                    sb.append("• *").append(r.getTestName()).append("*: ")
                            .append(r.getResultValue()).append(" ").append(r.getUnit());
                    if (r.getReferenceRange() != null && !r.getReferenceRange().isBlank()) {
                        sb.append(" (Ref: ").append(r.getReferenceRange()).append(")");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }

        if (found)
            return sb.toString();

        return "🔍 I found your blood test reports, but couldn't find a direct match for '" + testName + "'.\n" +
                "Try asking for a more general name, or check the full report link.";
    }

    // ── Helpers ──

    private RelationType parseRole(String role) {
        if (role == null || role.isBlank())
            return null;
        try {
            return RelationType.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown role '{}', defaulting to SELF", role);
            return RelationType.SELF;
        }
    }

    private LocalDate parseDate(String dateStr, LocalDate fallback) {
        if (dateStr == null || dateStr.isBlank())
            return fallback;
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            log.warn("Could not parse date '{}', using fallback {}", dateStr, fallback);
            return fallback;
        }
    }

    private void appendPrescriptionBlock(StringBuilder sb, Prescription p) {
        String patientName = p.getPerson() != null ? p.getPerson().getName() : "Unknown";
        String relation = p.getPerson() != null ? p.getPerson().getRelationType().name() : "";
        sb.append("👤 ").append(patientName);
        if (!relation.isEmpty())
            sb.append(" (").append(relation.toLowerCase()).append(")");
        sb.append("\n");
        sb.append("📅 Date: ").append(p.getVisitDate()).append("\n");
        sb.append("🏥 Hospital: ").append(p.getHospitalName() != null ? p.getHospitalName() : "N/A").append("\n");
        sb.append("👨‍⚕️ Doctor: ").append(p.getDoctorName() != null ? p.getDoctorName() : "N/A").append("\n");
        if (!p.getMedicines().isEmpty()) {
            sb.append("💊 Medicines:\n");
            p.getMedicines().forEach(m -> sb.append("  - ").append(m.getName())
                    .append(" (").append(m.getDosage() != null ? m.getDosage() : "N/A").append(")")
                    .append(m.getFrequency() != null ? " " + m.getFrequency() : "")
                    .append("\n"));
        }
        sb.append("🆔 ID: ").append(p.getId()).append("\n");
        sb.append("─────────────\n");
    }

    public String generatePresignedUrl(String s3Key) {
        return s3Service.generatePresignedUrl(s3Key);
    }
}
