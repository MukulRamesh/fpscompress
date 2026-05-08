
PrefabBlockEntity has grown large (1000+ lines). Consider future refactoring:

Extract transport logic → TransportManager class
Extract state machine → SimulationStateMachine class
Extract rate calculation → RateCalculator class
Keep PrefabBlockEntity as coordinator (delegates to managers)
This refactoring is not part of this plan (post-MVP polish), but should be documented for future work. Consider creating REFACTORING_PREFAB.md design doc.