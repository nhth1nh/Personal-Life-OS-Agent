package com.ploa.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Should parse tool plan with zero tools correctly")
    void shouldParseZeroToolsPlan() throws Exception {
        String json = "{\"tools\": []}";
        AssistantService.ToolPlan plan = mapper.readValue(json, AssistantService.ToolPlan.class);

        assertThat(plan.tools()).isEmpty();
        assertThat(plan.memory()).isNull();
    }

    @Test
    @DisplayName("Should parse memory tool plan with category, key, value")
    void shouldParseMemoryToolPlan() throws Exception {
        String json = """
                {
                  "tools": ["saveUserMemory"],
                  "date": "today",
                  "memory": {
                    "category": "PROFILE",
                    "key": "name",
                    "value": "Alex"
                  }
                }
                """;
        AssistantService.ToolPlan plan = mapper.readValue(json, AssistantService.ToolPlan.class);

        assertThat(plan.tools()).containsExactly("saveUserMemory");
        assertThat(plan.memory()).isNotNull();
        assertThat(plan.memory().category()).isEqualTo("PROFILE");
        assertThat(plan.memory().key()).isEqualTo("name");
        assertThat(plan.memory().value()).isEqualTo("Alex");
    }

    @Test
    @DisplayName("Should extract clean JSON when wrapped in markdown code fence")
    void shouldExtractCleanJsonFromMarkdown() throws Exception {
        String raw = """
                Here is the plan:
                ```json
                {
                  "tools": ["getDailyHealth"],
                  "date": "today"
                }
                ```
                Hope this helps!
                """;

        int firstBrace = raw.indexOf('{');
        int lastBrace = raw.lastIndexOf('}');
        String cleaned = raw.substring(firstBrace, lastBrace + 1);

        AssistantService.ToolPlan plan = mapper.readValue(cleaned, AssistantService.ToolPlan.class);
        assertThat(plan.tools()).containsExactly("getDailyHealth");
        assertThat(plan.date()).isEqualTo("today");
    }

    @Test
    @DisplayName("Should parse tool plan when tools is an array of objects with name and arguments")
    void shouldParseObjectArrayToolPlan() throws Exception {
        String json = """
                {
                  "tools": [
                    {
                      "name": "saveUserMemory",
                      "arguments": {
                        "category": "PREFERENCE",
                        "key": "lunch_routine_axon_ivy",
                        "value": "Company lunch Monday through Thursday"
                      }
                    }
                  ]
                }
                """;

        AssistantService assistantService = new AssistantService(null, null, null, org.mockito.Mockito.mock(org.springframework.ai.chat.client.ChatClient.Builder.class));
        AssistantService.ToolPlan plan = assistantService.parseToolPlan(json);

        assertThat(plan.tools()).containsExactly("saveUserMemory");
        assertThat(plan.memory()).isNotNull();
        assertThat(plan.memory().category()).isEqualTo("PREFERENCE");
        assertThat(plan.memory().key()).isEqualTo("lunch_routine_axon_ivy");
        assertThat(plan.memory().value()).isEqualTo("Company lunch Monday through Thursday");
    }
}
