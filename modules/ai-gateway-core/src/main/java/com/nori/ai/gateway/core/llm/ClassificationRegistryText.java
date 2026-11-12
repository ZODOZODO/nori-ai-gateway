package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.registry.Availability;
import com.nori.ai.gateway.core.registry.CapabilityManifest;
import com.nori.ai.gateway.core.registry.ImplementationPhase;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 분류 프롬프트 {@code {{REGISTRY}}} 의 참고 정보(SPEC §5.3 ⓑ: 시스템 이름·별칭·소유 업무 영역과 boundSystem 활성 기능 범위, 판정 조건 아님).
 * 줄 형식은 F0 v2 하네스 RegistryFixture.renderRegistry 와 같다.
 */
public final class ClassificationRegistryText {
    public record SystemArea(String system, List<String> aliases, List<String> ownedAreas) {
        public SystemArea {
            Objects.requireNonNull(system, "system");
            aliases = List.copyOf(aliases); ownedAreas = List.copyOf(ownedAreas);
            if (system.isBlank() || aliases.isEmpty() || ownedAreas.isEmpty()) throw new IllegalArgumentException("시스템 참고 정보가 비었습니다");
        }
    }

    /** {@code active} = IMPLEMENTED·활성 단계. 비활성은 단계 이름과 함께 적는다. */
    public record CapabilityLine(String capabilityId, boolean active, ImplementationPhase phase) {
        public CapabilityLine { Objects.requireNonNull(capabilityId, "capabilityId"); Objects.requireNonNull(phase, "phase"); }
    }

    private ClassificationRegistryText() {}

    /** boundSystem 소유 Manifest 를 Registry 순서대로 활성·비활성 줄로 바꾼다. */
    public static List<CapabilityLine> lines(OwnerSystem boundSystem, List<CapabilityManifest> manifests, Predicate<ImplementationPhase> activePhase) {
        List<CapabilityLine> lines = new ArrayList<>();
        for (CapabilityManifest manifest : manifests) {
            if (manifest.ownerSystem() != boundSystem) continue;
            lines.add(new CapabilityLine(manifest.capabilityId(), manifest.availability() == Availability.IMPLEMENTED && activePhase.test(manifest.implementationPhase()),
                manifest.implementationPhase()));
        }
        return List.copyOf(lines);
    }

    public static String render(OwnerSystem boundSystem, List<SystemArea> systems, List<CapabilityLine> capabilities) {
        StringBuilder text = new StringBuilder("boundSystem: ").append(boundSystem.name()).append('\n');
        for (SystemArea system : systems)
            text.append("- 시스템 ").append(system.system()).append(" (별칭: ").append(String.join(", ", system.aliases())).append(") 소유 영역: ")
                .append(String.join(", ", system.ownedAreas())).append('\n');
        List<String> active = new ArrayList<>(), inactive = new ArrayList<>();
        for (CapabilityLine line : capabilities) {
            if (line.active()) active.add(line.capabilityId());
            else inactive.add(line.capabilityId() + "(" + line.phase().name() + " 비활성)");
        }
        text.append("- ").append(boundSystem.name()).append(" 활성 기능(1차): ").append(String.join(", ", active)).append('\n');
        text.append("- ").append(boundSystem.name()).append(" 비활성 기능(후속 차수): ").append(String.join(", ", inactive)).append('\n');
        return text.toString();
    }
}
