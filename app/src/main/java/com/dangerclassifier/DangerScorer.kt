package com.dangerclassifier

object DangerScorer {

    // Base danger score (0–9) for each COCO-90 label
    private val dangerMap = mapOf(
        // Animals — wild
        "bear" to 9,
        "elephant" to 8,
        "snake" to 7,
        "horse" to 4,
        "cow" to 3,
        "zebra" to 3,
        "giraffe" to 2,
        "dog" to 3,
        "cat" to 1,
        "bird" to 1,
        "sheep" to 1,
        // Sharp / weapon items
        "knife" to 8,
        "scissors" to 5,
        "baseball bat" to 5,
        "fork" to 3,
        // Vehicles
        "train" to 7,
        "motorcycle" to 6,
        "truck" to 6,
        "car" to 5,
        "bus" to 5,
        "boat" to 3,
        "bicycle" to 2,
        "airplane" to 3,
        // Road environment signals
        "traffic light" to 2,
        "stop sign" to 2,
        "fire hydrant" to 1,
        "parking meter" to 1,
        // Electrical / heat appliances
        "oven" to 3,
        "toaster" to 2,
        "hair drier" to 2,
        "microwave" to 1,
        // Fragile / breakable
        "wine glass" to 2,
        "bottle" to 2,
        "vase" to 1,
        // Sports / activity gear
        "skateboard" to 2,
        "skis" to 2,
        "snowboard" to 2,
        "surfboard" to 2,
        "tennis racket" to 2,
        "kite" to 1,
        "frisbee" to 1,
        "sports ball" to 1,
        "baseball glove" to 1,
        // People
        "person" to 1,
        // Everything else: minimal to zero
        "tv" to 1,
        "backpack" to 0,
        "umbrella" to 1,
        "handbag" to 0,
        "tie" to 0,
        "suitcase" to 0,
        "spoon" to 1,
        "bowl" to 0,
        "cup" to 0,
        "banana" to 0,
        "apple" to 0,
        "sandwich" to 0,
        "orange" to 0,
        "broccoli" to 0,
        "carrot" to 0,
        "hot dog" to 0,
        "pizza" to 0,
        "donut" to 0,
        "cake" to 0,
        "chair" to 0,
        "couch" to 0,
        "potted plant" to 0,
        "bed" to 0,
        "dining table" to 0,
        "toilet" to 0,
        "laptop" to 0,
        "mouse" to 0,
        "remote" to 0,
        "keyboard" to 0,
        "cell phone" to 0,
        "sink" to 0,
        "refrigerator" to 0,
        "book" to 0,
        "clock" to 0,
        "teddy bear" to 0,
        "toothbrush" to 0,
        "bench" to 0
    )

    private val dangerDescriptions = mapOf(
        "bear" to "a bear is a highly dangerous apex predator capable of lethal attacks",
        "elephant" to "an elephant can trample and is extremely powerful",
        "snake" to "a snake may be venomous and pose a fatal bite risk",
        "knife" to "a knife is a lethal cutting weapon",
        "scissors" to "scissors are sharp cutting tools that can cause serious injury",
        "baseball bat" to "a bat can be used as a blunt-force weapon",
        "fork" to "a fork has sharp tines that can puncture skin",
        "train" to "a train poses extreme impact hazard — collision is almost always fatal",
        "motorcycle" to "a motorcycle is a high-speed vehicle with minimal occupant protection",
        "truck" to "a large truck can cause devastating collisions",
        "car" to "a vehicle in motion poses serious collision risk",
        "bus" to "a large bus vehicle is present",
        "boat" to "a watercraft poses drowning and collision risks",
        "bicycle" to "a bicycle moves in traffic and poses fall/collision risk",
        "airplane" to "an aircraft is visible",
        "horse" to "a horse can kick or trample with lethal force",
        "cow" to "cattle can charge and are deceptively dangerous",
        "zebra" to "a zebra can kick aggressively",
        "giraffe" to "a giraffe can kick with significant force",
        "dog" to "a dog could potentially bite or attack",
        "traffic light" to "you are in an active road environment",
        "stop sign" to "you are in a road environment",
        "oven" to "an oven poses serious burn and fire risk",
        "toaster" to "a toaster poses electrical and fire risk",
        "hair drier" to "an electrical device near potential water sources",
        "wine glass" to "glass can shatter into sharp fragments",
        "bottle" to "bottles can break and cause lacerations",
        "skateboard" to "a skateboard is a fall and collision hazard",
        "skis" to "skiing equipment is associated with high-speed fall risk",
        "person" to "a person is present in the scene"
    )

    fun analyze(detections: List<Pair<String, Float>>): DangerResult {
        if (detections.isEmpty()) {
            return DangerResult(
                score = 0,
                level = "SAFE",
                reasoning = "DETECTED: Nothing identifiable.\n\nASSESSMENT: No objects detected. The scene appears clear of recognizable hazards."
            )
        }

        // Weighted danger: base score × detection confidence
        val weighted = detections.map { (label, conf) ->
            val base = dangerMap[label.lowercase()] ?: 0
            Triple(label, conf, base * conf)
        }.sortedByDescending { it.third }

        val topWeighted = weighted.maxOf { it.third }
        val maxPossible = 9f  // bear has base score 9

        var score = (topWeighted / maxPossible * 10).toInt().coerceIn(0, 10)

        // +1 if multiple high-danger items detected simultaneously
        val highDangerCount = detections.count { (label, conf) ->
            (dangerMap[label.lowercase()] ?: 0) >= 5 && conf > 0.4f
        }
        if (highDangerCount > 1) score = (score + 1).coerceAtMost(10)

        // +1 if multiple vehicles (busy road / traffic scenario)
        val vehicleLabels = setOf("car", "truck", "bus", "motorcycle", "train", "bicycle")
        val vehicleCount = detections.count { (label, _) -> label.lowercase() in vehicleLabels }
        if (vehicleCount >= 3) score = (score + 1).coerceAtMost(10)

        val level = toLevel(score)
        val reasoning = buildReasoning(detections, weighted, score)
        return DangerResult(score, level, reasoning)
    }

    private fun toLevel(score: Int) = when (score) {
        0, 1, 2 -> "SAFE"
        3, 4    -> "LOW RISK"
        5, 6    -> "MODERATE"
        7, 8    -> "DANGEROUS"
        else    -> "EXTREME DANGER"
    }

    private fun buildReasoning(
        detections: List<Pair<String, Float>>,
        weighted: List<Triple<String, Float, Float>>,
        score: Int
    ): String {
        val sb = StringBuilder()

        // Section 1 — detected items
        val topItems = detections.take(5).joinToString(", ") { (label, conf) ->
            "$label (${(conf * 100).toInt()}%)"
        }
        sb.append("DETECTED: $topItems\n\n")

        // Section 2 — per-item explanation
        val explanations = weighted.take(3).mapNotNull { (label, _, _) ->
            dangerDescriptions[label.lowercase()]
        }
        if (explanations.isNotEmpty()) {
            sb.append("ANALYSIS: ")
            sb.append(explanations.joinToString("; ").replaceFirstChar { it.uppercase() })
            sb.append(".\n\n")
        }

        // Section 3 — overall assessment
        sb.append("ASSESSMENT: ")
        sb.append(
            when (score) {
                0, 1 -> "Scene appears completely safe. No significant hazards identified."
                2, 3 -> "Minimal risk. The environment is generally safe but minor hazards may be present."
                4, 5 -> "Moderate risk. Some potentially hazardous elements detected — exercise normal caution."
                6, 7 -> "Elevated danger. Significant hazards present; proceed carefully."
                8, 9 -> "High danger. Serious threats to personal safety detected — avoid if possible."
                10   -> "Extreme danger. Immediate life threat detected. Seek safety now."
                else -> "Unable to fully assess."
            }
        )

        return sb.toString()
    }
}
