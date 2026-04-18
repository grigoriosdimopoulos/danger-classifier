package com.dangerclassifier

object DangerScorer {

    // ── COCO-90 object detector labels ────────────────────────────────────────
    private val cocoMap = mapOf(
        "bear" to 9, "elephant" to 8, "snake" to 7,
        "horse" to 4, "cow" to 3, "zebra" to 3, "giraffe" to 2,
        "dog" to 3, "cat" to 1, "bird" to 1, "sheep" to 1,
        "knife" to 8, "scissors" to 5, "baseball bat" to 5, "fork" to 3,
        "train" to 7, "motorcycle" to 6, "truck" to 6, "car" to 5,
        "bus" to 5, "boat" to 3, "bicycle" to 2, "airplane" to 3,
        "traffic light" to 2, "stop sign" to 2, "fire hydrant" to 1, "parking meter" to 1,
        "oven" to 3, "toaster" to 2, "hair drier" to 2, "microwave" to 1,
        "wine glass" to 2, "bottle" to 2, "vase" to 1,
        "skateboard" to 2, "skis" to 2, "snowboard" to 2, "surfboard" to 2,
        "tennis racket" to 2, "kite" to 1, "frisbee" to 1,
        "sports ball" to 1, "baseball glove" to 1,
        "person" to 1, "tv" to 1, "backpack" to 0, "umbrella" to 1,
        "handbag" to 0, "tie" to 0, "suitcase" to 0, "spoon" to 1,
        "bowl" to 0, "cup" to 0, "banana" to 0, "apple" to 0,
        "sandwich" to 0, "orange" to 0, "broccoli" to 0, "carrot" to 0,
        "hot dog" to 0, "pizza" to 0, "donut" to 0, "cake" to 0,
        "chair" to 0, "couch" to 0, "potted plant" to 0, "bed" to 0,
        "dining table" to 0, "toilet" to 0, "laptop" to 0, "mouse" to 0,
        "remote" to 0, "keyboard" to 0, "cell phone" to 0, "sink" to 0,
        "refrigerator" to 0, "book" to 0, "clock" to 0,
        "teddy bear" to 0, "toothbrush" to 0, "bench" to 0
    )

    // ── Descriptions for reasoning text (COCO + key ImageNet additions) ───────
    private val descriptions = mapOf(
        "bear"          to "a bear is a highly dangerous apex predator",
        "elephant"      to "an elephant can trample and is extremely powerful",
        "snake"         to "a snake may be venomous and pose a fatal bite risk",
        "knife"         to "a knife is a lethal cutting weapon",
        "scissors"      to "scissors are sharp tools that can cause serious injury",
        "baseball bat"  to "a bat can be used as a blunt-force weapon",
        "fork"          to "a fork has sharp tines that can puncture skin",
        "train"         to "a train poses extreme impact hazard",
        "motorcycle"    to "a motorcycle is a high-speed vehicle with minimal protection",
        "truck"         to "a large truck can cause devastating collisions",
        "car"           to "a vehicle in motion poses serious collision risk",
        "bus"           to "a large bus is present",
        "horse"         to "a horse can kick or trample with lethal force",
        "cow"           to "cattle can charge and are deceptively dangerous",
        "dog"           to "a dog could bite or attack",
        "traffic light" to "you are in an active road environment",
        "oven"          to "an oven poses serious burn and fire risk",
        "toaster"       to "a toaster poses electrical and fire risk",
        "wine glass"    to "glass can shatter into sharp fragments",
        // ImageNet-specific
        "revolver"      to "a revolver is a deadly firearm",
        "rifle"         to "a rifle is a high-powered firearm",
        "assault rifle" to "an assault rifle is an extremely dangerous automatic weapon",
        "chain saw"     to "a chainsaw is a lethal cutting power tool",
        "chainsaw"      to "a chainsaw is a lethal cutting power tool",
        "cleaver"       to "a meat cleaver is a heavy bladed weapon",
        "hatchet"       to "a hatchet is a short-handled axe capable of lethal injury",
        "pocketknife"   to "a folding knife (such as a Swiss Army / Victorinox) is a concealed blade",
        "jackknife"     to "a jackknife is a folding blade that can cause serious cuts",
        "letter opener" to "a sharp letter opener can puncture and slash",
        "dagger"        to "a dagger is a double-edged stabbing weapon",
        "flamethrower"  to "a flamethrower projects burning fuel and is extremely dangerous",
        "lighter"       to "an open flame source poses fire and burn risk",
        "candle"        to "an open flame poses fire risk in enclosed spaces",
        "syringe"       to "a syringe poses needlestick injury and contamination risk",
        "lion"          to "a lion is an apex predator capable of fatal attacks",
        "tiger"         to "a tiger is a powerful predator with lethal claws and bite",
        "cheetah"       to "a cheetah is a fast and dangerous big cat",
        "cougar"        to "a cougar (mountain lion) can stalk and attack humans",
        "leopard"       to "a leopard is an ambush predator",
        "jaguar"        to "a jaguar has one of the most powerful bites of any big cat",
        "rattlesnake"   to "a rattlesnake is venomous and potentially lethal",
        "cobra"         to "a cobra's venom can cause death within hours",
        "mamba"         to "a mamba is one of the fastest and most venomous snakes",
        "viper"         to "a viper is a venomous snake",
        "boa constrictor" to "a boa constrictor kills by suffocation",
        "python"        to "a large python kills by constriction",
        "great white shark" to "a great white shark is the apex ocean predator",
        "tiger shark"   to "a tiger shark is an aggressive and dangerous predator",
        "shark"         to "a shark is a dangerous marine predator",
        "black widow"   to "a black widow spider's venom attacks the nervous system",
        "tarantula"     to "a tarantula has venomous fangs and urticating hairs",
        "scorpion"      to "a scorpion sting can cause severe pain and anaphylaxis",
        "volcano"       to "volcanic activity poses lava, gas, and pyroclastic hazards",
        "cliff"         to "a cliff edge poses extreme fall risk",
        "band saw"      to "a band saw can cause severe lacerations",
        "circular saw"  to "a circular saw causes severe lacerations",
        "power drill"   to "a power drill poses puncture and laceration risk",
        "warplane"      to "military aircraft indicates combat or conflict",
        "tank"          to "an armoured tank is an extreme combat threat",
        "cannon"        to "a cannon is a high-powered ballistic weapon"
    )

    // ── ImageNet label danger scoring (substring matching) ───────────────────
    //
    // ImageNet labels are long strings with synonyms, e.g.
    // "revolver, six-gun, six-shooter" or "chain saw, chainsaw"
    // We match by checking if any keyword is contained in the lowercase label.

    fun scoreImageNetLabel(label: String): Int {
        val l = label.lowercase()
        return when {
            // Firearms
            "revolver" in l || "six-gun" in l                         -> 9
            "rifle" in l                                               -> 9
            "assault rifle" in l || "assault gun" in l                -> 10
            "shotgun" in l                                             -> 9
            "handgun" in l || "pistol" in l                           -> 9
            "submachine" in l || "machine gun" in l                   -> 10

            // Bladed weapons / knives
            "chain saw" in l || "chainsaw" in l                       -> 8
            "meat cleaver" in l || "cleaver" in l                     -> 7
            "hatchet" in l                                             -> 7
            "dagger" in l || "stiletto" in l                          -> 8
            "switchblade" in l || "jackknife" in l                    -> 7
            "pocketknife" in l || "pocket knife" in l                 -> 7
            "letter opener" in l || "paper knife" in l                -> 5
            "scalpel" in l                                             -> 6
            "saber" in l || "sabre" in l || "rapier" in l             -> 8
            "sword" in l                                               -> 8
            "pike" in l || "lance" in l || "spear" in l               -> 7
            "battleaxe" in l || "battle axe" in l || "war axe" in l   -> 8

            // Power tools
            "band saw" in l || "bandsaw" in l                         -> 7
            "circular saw" in l || "buzz saw" in l                    -> 7
            "power drill" in l                                         -> 5
            "nail gun" in l                                            -> 7
            "jigsaw" in l && "puzzle" !in l                           -> 6

            // Fire / heat hazards
            "flamethrower" in l                                        -> 10
            "lighter" in l || "igniter" in l || "ignitor" in l        -> 5
            "candle" in l || "taper" in l                             -> 4
            "matchstick" in l                                          -> 3
            "space heater" in l                                        -> 4
            "welding" in l || "blowtorch" in l || "blow torch" in l   -> 7

            // Medical / chemical
            "syringe" in l                                             -> 6
            "scalpel" in l                                             -> 6

            // Big cats (ORDER MATTERS — check specific before generic)
            "lion" in l && "dandelion" !in l && "sea lion" !in l      -> 9
            "tiger" in l && "tiger shark" !in l                       -> 9
            "cheetah" in l                                             -> 8
            "cougar" in l || "puma" in l || "mountain lion" in l      -> 8
            "leopard" in l || "jaguar" in l                           -> 8
            "panther" in l                                             -> 8
            "lynx" in l || "bobcat" in l                              -> 6
            "snow leopard" in l                                        -> 8

            // Bears
            "brown bear" in l || "polar bear" in l || "grizzly" in l  -> 9
            "black bear" in l || "sun bear" in l || "sloth bear" in l -> 8
            ("bear" in l) && ("koala" !in l) && ("panda" !in l)      -> 8

            // Wolves / wild dogs
            "wolf" in l || "wolves" in l                              -> 7
            "hyena" in l || "coyote" in l                             -> 5

            // Venomous snakes (specific before generic)
            "diamondback" in l || "rattlesnake" in l                  -> 9
            "cobra" in l || "king cobra" in l                         -> 9
            "mamba" in l                                               -> 9
            "viper" in l || "fer-de-lance" in l || "bushmaster" in l  -> 9
            "coral snake" in l                                         -> 9
            "boa constrictor" in l || "anaconda" in l                 -> 8
            "python" in l || "rock python" in l                       -> 8
            "sidewinder" in l || "horned viper" in l                  -> 9

            // Sharks
            "great white shark" in l || "man-eating shark" in l       -> 9
            "tiger shark" in l || "bull shark" in l                   -> 9
            "hammerhead" in l                                          -> 8
            "mako" in l                                                -> 8
            ("shark" in l) && ("card shark" !in l)                    -> 7

            // Spiders / arachnids
            "black widow" in l                                         -> 8
            "tarantula" in l                                           -> 6
            "scorpion" in l                                            -> 7

            // Large dangerous reptiles
            "crocodile" in l || "alligator" in l || "caiman" in l     -> 8
            "komodo" in l                                              -> 8

            // Structural / environmental
            "volcano" in l || "volcanic" in l                         -> 10
            "cliff" in l || "precipice" in l                          -> 7
            "avalanche" in l                                           -> 9

            // Military hardware
            "warplane" in l || "fighter jet" in l || "military plane" in l -> 8
            ("tank" in l) && ("fish tank" !in l) && ("water tank" !in l)
                    && ("storage tank" !in l)                          -> 8
            "cannon" in l || "howitzer" in l                          -> 8
            "mortar" in l && "pestle" !in l                           -> 8
            "missile" in l || "projectile" in l                       -> 9
            "grenade" in l || "bomb" in l                             -> 10
            "landmine" in l || "mine" in l && "mine car" !in l        -> 10

            else                                                       -> 0
        }
    }

    fun getDangerScore(label: String): Int = cocoMap[label.lowercase()] ?: 0

    // ── Analysis ─────────────────────────────────────────────────────────────

    fun analyze(
        boxes: List<DetectionBox>,
        sceneLabels: List<SceneLabel> = emptyList()
    ): DangerResult {

        // --- Score from object detector boxes ---
        val detectorScore = if (boxes.isEmpty()) 0 else {
            val topWeighted = boxes.maxOf { it.dangerScore * it.confidence }
            (topWeighted / 9f * 10).toInt().coerceIn(0, 10)
        }.let { s ->
            var adjusted = s
            if (boxes.count { it.dangerScore >= 5 && it.confidence > 0.4f } > 1)
                adjusted = (adjusted + 1).coerceAtMost(10)
            val vehicleLabels = setOf("car","truck","bus","motorcycle","train","bicycle")
            if (boxes.count { it.label.lowercase() in vehicleLabels } >= 3)
                adjusted = (adjusted + 1).coerceAtMost(10)
            adjusted
        }

        // --- Score from ImageNet scene classifier ---
        val classifierScore = if (sceneLabels.isEmpty()) 0 else {
            val topWeighted = sceneLabels.maxOf { it.dangerScore * it.confidence }
            (topWeighted / 9f * 10).toInt().coerceIn(0, 10)
        }

        // Final score: whichever source sees more danger wins
        val score = maxOf(detectorScore, classifierScore)
        val level = toLevel(score)

        // Boxes to highlight in the overlay
        val highlightBoxes = boxes
            .filter { it.dangerScore >= 4 && it.confidence >= 0.40f }
            .sortedByDescending { it.dangerScore * it.confidence }
            .take(3)

        val reasoning = buildReasoning(boxes, sceneLabels, score)
        return DangerResult(score, level, reasoning, highlightBoxes)
    }

    private fun toLevel(score: Int) = when (score) {
        0, 1, 2 -> "SAFE"
        3, 4    -> "LOW RISK"
        5, 6    -> "MODERATE"
        7, 8    -> "DANGEROUS"
        else    -> "EXTREME DANGER"
    }

    private fun buildReasoning(
        boxes: List<DetectionBox>,
        sceneLabels: List<SceneLabel>,
        score: Int
    ): String = buildString {

        // Object detector results
        if (boxes.isNotEmpty()) {
            val topItems = boxes.take(5).joinToString(", ") {
                "${it.label} (${(it.confidence * 100).toInt()}%)"
            }
            append("DETECTED: $topItems\n\n")
        }

        // Scene classifier — only list items that score > 0
        val dangerousScene = sceneLabels
            .filter { it.dangerScore > 0 && it.confidence > 0.06f }
            .sortedByDescending { it.dangerScore * it.confidence }
            .take(4)
        if (dangerousScene.isNotEmpty()) {
            val items = dangerousScene.joinToString(", ") { s ->
                // Use only the first synonym from ImageNet's comma-joined label
                val shortLabel = s.label.split(",").first().trim()
                "$shortLabel (${(s.confidence * 100).toInt()}%)"
            }
            append("SCENE CONTEXT: $items\n\n")
        }

        // Per-item explanation (merge box labels + dangerous scene labels)
        val allLabels = (boxes.map { it.label } +
                dangerousScene.map { it.label.split(",").first().trim() })
            .distinct().take(4)
        val explanations = allLabels.mapNotNull { label ->
            descriptions[label.lowercase()]
                ?: descriptions[label.lowercase().split(",").first().trim()]
        }
        if (explanations.isNotEmpty()) {
            append("ANALYSIS: ")
            append(explanations.joinToString("; ").replaceFirstChar { it.uppercase() })
            append(".\n\n")
        }

        // Overall assessment
        append("ASSESSMENT: ")
        append(when (score) {
            0, 1 -> "Scene appears completely safe. No hazards identified."
            2, 3 -> "Minimal risk. Generally safe, minor hazards may be present."
            4, 5 -> "Moderate risk. Hazardous elements detected — exercise normal caution."
            6, 7 -> "Elevated danger. Significant hazards present; proceed carefully."
            8, 9 -> "High danger. Serious threats to personal safety — avoid if possible."
            10   -> "Extreme danger. Immediate life threat. Seek safety now."
            else -> "Unable to fully assess."
        })
    }
}
