# Brainstorming: Medical Bot Post-Prescription Features

## 1. Where do users ask "What was my creatinine?"
Right now, the bot has a conversational text input box where users say things like "Show my prescriptions" or "Analyze my health". We use Spring AI with OpenAI in `AIService.parseIntent` to figure out what the user means.

To add the ability to ask about specific tests (like creatinine, hemoglobin, etc.), we would add **a new Intent** to our AI parser:

*   **New Intent:** `QUERY_TEST_RESULT`
*   **Trigger examples:** "What was my creatinine?", "Show my latest hemoglobin", "Did my liver enzymes improve?"
*   **How it works:**
    1. The AI Intent Parser detects `QUERY_TEST_RESULT` and extracts the test name (`creatinine`) and the person (`SELF` or `MOTHER`).
    2. We take that test name and perform a **Vector Search** (or SQL query) in our Blood Test Database for that specific user.
        *   **How we link it to a prescription:** The vector entries will be "tagged" with metadata, like the `prescription_id` or the `date_uploaded`. So when the user asks, the bot can say: *"On your Oct 5th visit to Apollo Hospital, your creatinine was..."*
    3. We feed the resulting data (e.g., *Date: Oct 5, Creatinine: 1.1 mg/dL, Unit: mg/dL, Range: 0.7-1.3*) back to the AI.
    4. The AI formulates a conversational answer: *"Your last creatinine level was 1.1 mg/dL on Oct 5th, which is within the normal range."*

---

## 2. What else can a doctor give/prescribe?
Apart from Medicines, Blood Tests, and X-Rays/MRIs (Scans), doctors often provide:

1.  **Vaccination Records / Immunization Charts:** (Especially for kids, travel, or flu shots). We could parse these and automatically schedule future reminders.
2.  **Referral Letters / Consultant Notes:** A letter saying "Please see Dr. Smith, Cardiologist, for suspected arrhythmia". 
3.  **Diet / Nutrition Plans:** Strict dietary restrictions (e.g., "Low sodium diet", "Diabetic meal plan").
4.  **Physiotherapy / Exercise Routines:** Specific stretches or physical therapy prescriptions.
5.  **Medical Bills / Invoices (Optional Attachment):** While not clinical, users often want to track medical expenses alongside prescriptions for insurance claims.
6.  **Discharge Summaries (Optional Attachment):** If they were hospitalized, the discharge summary is a goldmine of data (diagnosis, timeline, procedures done).

---

## 3. What else can OUR App do? (Future Abilities)
Since we already have a powerful AI and a robust backend, here are some really impactful features we could add:

### 🟢 Easy Wins (High Impact, Low Effort)
*   **💊 Pill Reminder / Alarms:** Since we already know the medicine, frequency, and duration (e.g., "Paracetamol 500mg, twice a day for 5 days"), we could integrate with Spring Scheduler or Telegram's scheduled messages to send reminders: *"Hey! Time to take your Paracetamol!"*
*   **🔄 Refill Reminders:** If a prescription says "Atorvastatin 1 tablet daily for 30 days", on day 25 the bot could proactively message: *"You might be running low on Atorvastatin. Time to see the doctor for a refill?"*
*   **�� Follow-up Reminders:** Often a doctor writes "Review after 2 weeks". We can extract that and remind the user to book an appointment.

### 🟡 Medium Effort (Medical Intelligence)
*   **⚠️ Drug Interaction Checking:** When a user uploads a *new* prescription, the AI can cross-reference the *new* medicines with the *existing* medicines they are already taking (from the database) and warn them: *"Wait! This new antibiotic might interact badly with your current blood pressure medication. Please verify with your pharmacist."*
*   **🤧 Symptom & Vitals Journaling:** Users can text the bot: *"My blood pressure today is 130/85"* or *"I have a slight fever today"*. The bot logs this. Before their next doctor visit, they can ask: *"Summarize my symptoms for the last month"*, and the bot outputs a neat timeline.
*   **💰 Medical Expense Tracker (Approved):** Since we will optional allow uploading medical bills, we can sum up their total spending per month/year (great for tax purposes).

### 🔴 Advanced (Deep AI Integration)
*   **📊 Longitudinal Trend Graphs:** Instead of just telling the user their creatinine value, we generate an actual image/chart showing their creatinine levels over the last 5 years to show if it's trending upward or downward, and send the chart image in Telegram.
*   **🗣️ Voice Input Support:** People hate typing long medical queries. Telegram supports Voice Notes. We could pass voice notes to OpenAI's Whisper API to transcribe them, then process them normally. *"Hey bot, add a note that my knee started hurting again today after my morning run."*

---

## 4. Summary of the Proposed Architecture Update

To support the immediate goals (Attachments + Vector DB Analysis), our system will evolve to:

1.  **New Entities:** `Attachment` (Type: Image/PDF), `TestResultChunk` (Vector Embeddings).
2.  **Profile Upgrades:** Add `Age/DOB` and `Gender` to `Person` for accurate AI analysis.
3.  **Bot UX:** Use **Telegram Inline Buttons** for the post-prescription flow (`[Add Blood Test]`, `[Done]`).
4.  **Processing Pipeline:**
    *   Image/PDF uploaded -> AI Vision/PDFBox extracts raw text.
    *   Text is chunked -> OpenAI Embeddings API -> Saved to VectorDB (PGVector).
        *   **What are OpenAI Embeddings?** *An embedding is just converting a sentence into a long list of numbers (a vector) that represents its meaning. For example, "Kidney Function Test" and "Creatinine Level" will have very similar number lists because they mean similar things. When a user asks "How are my kidneys?", we turn that question into a number list, find the text chunks with the closest matching numbers in our Vector DB, and give those chunks to the AI to read so it can answer the question!*
5.  **Intent Engine Updates:** Add `QUERY_TEST_RESULT` to `AIService` prompts.
