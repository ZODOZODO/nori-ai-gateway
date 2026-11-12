package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.scope.OwnerSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 시스템별 승인 근거 프로필(SPEC §28.2 "프로필에는 각 근거의 제공 Capability, 입력·출력 바인딩, 선행 근거, 필수/선택, 실패 시 보고할 누락 항목을 선언").
 * 구조 불변식(중복 ID·선행 순서·순환·C 직접 제공 금지·바인딩 출처)은 생성 시 검사하고, Registry 대조(owner·Capability·Mutation·입력 이름·출력 경로)는
 * 명시 loader 가 한다. 임의 script·SpEL·SQL·URL·Source 경로 실행 필드는 형식에 없다.
 */
public record EvidencePlanProfile(OwnerSystem ownerSystem, String version, List<Entry> entries) {
    /** 바인딩 출처: 승인된 argument 이름 또는 선행 근거 단계 결과의 등록 필드 경로. */
    public enum InputSource { ARGUMENT, STEP_FIELD }

    public record Input(String name, InputSource source, String entryId, String fieldPath) {
        public Input {
            Objects.requireNonNull(name, "name"); Objects.requireNonNull(source, "source");
            if (name.isBlank() || source == InputSource.ARGUMENT && (entryId != null || fieldPath != null)
                || source == InputSource.STEP_FIELD && (entryId == null || entryId.isBlank() || fieldPath == null || fieldPath.isBlank()))
                throw new IllegalArgumentException("근거 입력 바인딩이 잘못되었습니다");
        }
    }

    /**
     * @param reportItems 이 근거가 채우는 보고 항목(예: 설비 상태)
     * @param unavailableItems 현재 READ 가 제공하지 않아 요청 시 명시적 누락으로 남길 항목(예: Port 목록)
     */
    public record Entry(String id, EvidenceNeed need, String capabilityId, boolean required, List<Input> inputs, List<String> dependsOn,
            List<String> reportItems, List<String> unavailableItems) {
        public Entry {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(need, "need"); Objects.requireNonNull(capabilityId, "capabilityId");
            inputs = List.copyOf(inputs); dependsOn = List.copyOf(dependsOn); reportItems = List.copyOf(reportItems); unavailableItems = List.copyOf(unavailableItems);
            // C 는 N/I 와 S/H 근거를 잇는 파생 비교이며 원천 Capability 로 직접 제공하지 않는다(SPEC §28.2).
            if (id.isBlank() || capabilityId.isBlank() || need == EvidenceNeed.C || new HashSet<>(dependsOn).size() != dependsOn.size())
                throw new IllegalArgumentException("근거 프로필 항목이 잘못되었습니다");
        }
    }

    public EvidencePlanProfile {
        Objects.requireNonNull(ownerSystem, "ownerSystem"); Objects.requireNonNull(version, "version");
        entries = List.copyOf(entries);
        if (version.isBlank() || entries.isEmpty()) throw new IllegalArgumentException("근거 프로필이 비었습니다");
        // 선행 근거는 앞에 선언된 항목만 가리킨다 — 중복 ID 와 순환이 생길 수 없다.
        Set<String> declared = new HashSet<>();
        for (Entry entry : entries) {
            if (!declared.containsAll(entry.dependsOn())) throw new IllegalArgumentException("근거 프로필 의존성이 잘못되었습니다");
            for (Input input : entry.inputs())
                if (input.source() == InputSource.STEP_FIELD && !entry.dependsOn().contains(input.entryId())) throw new IllegalArgumentException("근거 입력 바인딩이 선행 근거가 아닙니다");
            if (!declared.add(entry.id())) throw new IllegalArgumentException("근거 프로필 ID 가 중복되었습니다");
        }
    }

    public List<Entry> entriesFor(EvidenceNeed need) {
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : entries) if (entry.need() == need) matches.add(entry);
        return List.copyOf(matches);
    }

    public Entry entry(String id) {
        Map<String,Entry> byId = new HashMap<>();
        entries.forEach(entry -> byId.put(entry.id(), entry));
        Entry entry = byId.get(id);
        if (entry == null) throw new IllegalArgumentException("근거 프로필 항목이 없습니다");
        return entry;
    }
}
