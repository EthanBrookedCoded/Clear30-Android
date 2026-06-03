// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting_started/setup_your_environment
// This enables autocomplete, go to definition, etc.

// Setup type definitions for built-in Supabase Runtime APIs
import "jsr:@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.38.4'

// Define interfaces for our feedback response structures
interface ProgramNormativeFeedback {
  scoreCards: ProgramNormativeFeedbackScore[];
  amountCards: ProgramNormativeFeedbackNumber[];
  sections: ProgramNormativeFeedbackTextSection[];
}

interface ProgramNormativeFeedbackScore {
  title: string;
  score: number;
  insight: string;
  color1: string;
  color2: string;
}

interface ProgramNormativeFeedbackNumber {
  amount: string;
  title: string;
  subtitle: string;
  color1: string;
  color2: string;
}

interface ProgramNormativeFeedbackTextSection {
  sectionTitle: string;
  content: ProgramNormativeTextSectionContent[];
}

interface ProgramNormativeTextSectionContent {
  title: string;
  subtitle: string;
}

interface ProgramNormativeTextSectionContentOrdered {
  title: string;
  description: string;
  priority: number;
}

interface RequestData {
  userId: string;
  lastSmokedDate?: string; // Format: "YYYY-MM-DD"
  startDate?: string;      // Format: "YYYY-MM-DD"
}

interface CannabisUseFrequency {
  frequency: string;
  percentage: number;
  percentile_more_than: number;
  notes: string;
}

interface NormativeData {
  cannabis_use_frequency: CannabisUseFrequency[];
}

// Break reason types
const BreakReasonType = {
  gainMentalClarity: "Gain Mental Clarity",
  reduceAnxiety: "Reduce Anxiety",
  reduceDepression: "Reduce Depression",
  reduceBeingStuckInOwnHead: "Reduce Being Stuck in Own Head",
  improveSleepQuality: "Improve Sleep Quality",
  improveSelfControl: "Improve Self-Control and Intention",
  reduceDependency: "Reduce Dependency on Cannabis",
  exploreLifeWithout: "Explore Life Without Cannabis",
  lowerTolerance: "Lower Tolerance",
  improveOverallHealth: "Improve Overall Health",
  improveLungHealth: "Improve Lung Health",
  increaseProductivity: "Increase Productivity",
  increaseMotivation: "Increase Motivation",
  saveMoney: "Save Money",
  improveRelationships: "Improve Current Relationships",
  enhanceSocialConnections: "Enhance Social Connections",
  passDrugTest: "Pass Work-Required Drug Test",
  meetLegalObligations: "Meet Legal Obligations",
  other: "Other",

  // Helper method to convert from text to type
  fromText: function (text: string) {
    for (const [key, value] of Object.entries(this)) {
      if (value === text && typeof value === 'string') {
        return key;
      }
    }
    return "improveOverallHealth"; // Default value
  }
};

// AssessmentQuestionID mapping
const AssessmentQuestionID = {
  name: "Name-Exclude",
  breakReason: "Break-Reason",
  consumptionMethod: "Consumption-Method",
  daysUsing: "Days-Using",
  moneySpent: "Money-Spent",
  helpHarm: "Help-Harm",
  startDate: "Start-Date",
  previousBreak: "Previous-Break",
  trigger: "Trigger",
  thenWhat: "Then-What",
  commitment: "Commitment",
  lastSmoked: "Last-Smoked",
  loUseState: "LO-Use-State",
  newBreakType: "New-Break-Type"
};

Deno.serve(async (req) => {
  try {
    // Initialize Supabase client
    const supabaseUrl = Deno.env.get('SUPABASE_URL') as string
    const supabaseKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') as string
    const supabase = createClient(supabaseUrl, supabaseKey)

    // Get request data
    const requestData: RequestData = await req.json()
    const { userId, lastSmokedDate, startDate } = requestData

    if (!userId) {
      return new Response(
        JSON.stringify({ error: 'User ID is required' }),
        { headers: { "Content-Type": "application/json" }, status: 400 }
      )
    }

    // Fetch the user's most recent assessment responses
    const { data: assessmentData, error } = await supabase
      .schema('programs')
      .from('program_assessment_responses')
      .select('responses')
      .eq('user_id', userId)
      .order('timestamp', { ascending: false })
      .limit(1)

    if (error) {
      return new Response(
        JSON.stringify({ error: error.message }),
        { headers: { "Content-Type": "application/json" }, status: 500 }
      )
    }

    if (!assessmentData || assessmentData.length === 0) {
      return new Response(
        JSON.stringify({ error: 'No assessment data found for user' }),
        { headers: { "Content-Type": "application/json" }, status: 404 }
      )
    }

    // Fetch normative data
    const { data: normativeData, error: normativeError } = await supabase
      .schema('library')
      .from('one_offs')
      .select('*')
      .eq('key', 'normative_data')
      .limit(1)

    if (normativeError || !normativeData || normativeData.length === 0) {
      return new Response(
        JSON.stringify({ error: normativeError?.message || 'No normative data found' }),
        { headers: { "Content-Type": "application/json" }, status: 500 }
      )
    }

    // Generate the normative feedback using the responses and dates
    const responses = assessmentData[0].responses
    const feedback = generateCompleteNormativeFeedback(responses, lastSmokedDate, startDate, normativeData[0])

    return new Response(
      JSON.stringify(feedback),
      { headers: { "Content-Type": "application/json" }, status: 200 }
    )
  } catch (error) {
    console.error('Error in edge function:', error)
    return new Response(
      JSON.stringify({ error: error.message || 'An unknown error occurred' }),
      { headers: { "Content-Type": "application/json" }, status: 500 }
    )
  }
})

function generateCompleteNormativeFeedback(responses: any, lastSmokedDate?: string, startDate?: string, normativeData: any): ProgramNormativeFeedback {
  // Calculate numeric feedback
  const currentUseScore = calculateCurrentUseScore(responses, normativeData)
  const habitStrength = calculateHabitStrength(responses)
  const monthlySavings = calculateMonthlySavings(responses)

  // Get user's selected goal
  const breakReasonResponse = responses[AssessmentQuestionID.breakReason] || []
  const selectedGoalText = breakReasonResponse[0] || "Improve Overall Health"

  // Get top strengths and growth areas
  const strengths = getStrengths(responses, lastSmokedDate, startDate)
  const growthAreas = getGrowthAreas(responses)

  // Create score cards
  const scoreCards = [
    createCurrentUseScoreCard(currentUseScore),
    createHabitStrengthScoreCard(habitStrength)
  ]

  // Create amount card for financial opportunity
  const financialAmountCard = createFinancialOpportunityAmountCard(monthlySavings)

  // Convert strengths and growth areas to sections
  const sections = [
    createStrengthsSection(strengths),
    createGrowthAreasSection(growthAreas)
  ]

  return {
    scoreCards: scoreCards,
    amountCards: [financialAmountCard],
    sections: sections
  }
}

// MARK: - Score Card Creation
function createHabitStrengthScoreCard(score: number): ProgramNormativeFeedbackScore {
  return {
    title: "Habit Strength 🔄",
    score: score,
    insight: getHabitStrengthInsight(score),
    color1: "#5BB4A9",  // Default color 1 for clear30Gradient
    color2: "#80C97A"   // Default color 2 for clear30Gradient
  }
}

function createCurrentUseScoreCard(score: number): ProgramNormativeFeedbackScore {
  return {
    title: "Current Use 🍃",
    score: score,
    insight: getCurrentUseInsight(score),
    color1: "#FF416C",
    color2: "#FF4B2B"
  }
}

// MARK: - Amount Card Creation
function createFinancialOpportunityAmountCard(monthlySavings: number): ProgramNormativeFeedbackNumber {
  return {
    amount: `$${monthlySavings.toFixed(2)}`,
    title: "💰 Financial Opportunity",
    subtitle: "Estimated Monthly Savings:",
    color1: "#5B9CF0",
    color2: "#5BAEE6"
  }
}

// MARK: - Section Creation
function createStrengthsSection(strengths: ProgramNormativeTextSectionContentOrdered[]): ProgramNormativeFeedbackTextSection {
  const strengthContents: ProgramNormativeTextSectionContent[] = strengths.map(strength => ({
    title: strength.title,
    subtitle: strength.description
  }))

  return {
    sectionTitle: "🌟 Strengths",
    content: strengthContents
  }
}

function createGrowthAreasSection(growthAreas: ProgramNormativeTextSectionContentOrdered[]): ProgramNormativeFeedbackTextSection {
  const growthAreaContents: ProgramNormativeTextSectionContent[] = growthAreas.map(growthArea => ({
    title: growthArea.title,
    subtitle: growthArea.description
  }))

  return {
    sectionTitle: "🌱 Areas for Exploration",
    content: growthAreaContents
  }
}

// MARK: - Score Calculation Methods
function calculateCurrentUseScore(responses: any, normativeData: any): number {
  // Parse the normative data from text to structured format
  let parsedNormativeData: CannabisUseFrequency[];
  try {
    parsedNormativeData = JSON.parse(normativeData.value);
  } catch (error) {
    console.error('Error parsing normative data:', error);
    return 82; // Default score if parsing fails
  }

  const daysUsingResponse = responses[AssessmentQuestionID.daysUsing]?.[0] || "About once a week or less"

  // Map the response to the closest frequency in normative data
  let frequencyKey: string;
  switch (daysUsingResponse) {
    // New granular numeric format (1-7 days)
    case "1": frequencyKey = "1–2 times per week"; break;
    case "2": frequencyKey = "1–2 times per week"; break;
    case "3": frequencyKey = "3–4 times per week"; break;
    case "4": frequencyKey = "3–4 times per week"; break;
    case "5": frequencyKey = "5–6 times per week"; break;
    case "6": frequencyKey = "5–6 times per week"; break;
    case "7": frequencyKey = "Daily (every day)"; break;
    // Legacy range format (for backward compatibility)
    case "About once a week or less": frequencyKey = "1–2 times per week"; break;
    case "2-3 days a week": frequencyKey = "3–4 times per week"; break;
    case "4-5 days a week": frequencyKey = "5–6 times per week"; break;
    case "6-7 days a week": frequencyKey = "Daily (every day)"; break;
    default: frequencyKey = "1–2 times per week";
  }

  // Find the matching frequency in normative data
  const matchingFrequency = parsedNormativeData.find(
    f => f.frequency === frequencyKey
  );

  // Return the percentile_more_than value, or default to 50 if not found
  return matchingFrequency?.percentile_more_than || 82;
}

function calculateHabitStrength(responses: any): number {
  // Get responses
  const daysUsingResponse = responses[AssessmentQuestionID.daysUsing]?.[0] || "About once a week or less"

  // Map response to new days/week and base percentage
  let baseScore: number
  switch (daysUsingResponse) {
    // New granular numeric format (1-7 days)
    case "1":
      baseScore = 40;
      break;
    case "2":
      baseScore = 50;
      break;
    case "3":
      baseScore = 65;
      break;
    case "4":
      baseScore = 75;
      break;
    case "5":
      baseScore = 80;
      break;
    case "6":
      baseScore = 90;
      break;
    case "7":
      baseScore = 95;
      break;
    // Legacy range format (for backward compatibility)
    case "About once a week or less":
      baseScore = 40;
      break;
    case "2-3 days a week":
      baseScore = 65;
      break;
    case "4-5 days a week":
      baseScore = 80;
      break;
    case "6-7 days a week":
      baseScore = 95;
      break;
    default:
      baseScore = 40;
  }

  // Check if "Out of habit" or "Reduce/stop withdrawal symptoms" is selected
  const triggers = responses[AssessmentQuestionID.trigger] || []
  let triggerBonus = 0
  if (triggers.includes("Out of habit")) {
    triggerBonus = triggerBonus + 10
  }
  if (triggers.includes("Reduce/stop withdrawal symptoms")) {
    triggerBonus = triggerBonus + 10
  }

  // Cap at 100
  let finalScore = baseScore + triggerBonus
  finalScore = Math.min(100, finalScore)

  return finalScore
}

function calculateMonthlySavings(responses: any): number {
  // Get weekly spending amount
  const moneySpentResponse = responses[AssessmentQuestionID.moneySpent]?.[0] || "5"

  // Extract numeric value from the response
  const weeklyAmount = parseFloat(moneySpentResponse)

  // If money spent is 0, return 0 for monthly savings
  if (weeklyAmount === 0) {
    return 0
  }

  // Add a small random variance to the monthly savings
  const randomVariance = (Math.random() * 1.98) - 0.99

  return (weeklyAmount * 4.3) + randomVariance  // 4.3 average weeks in a month plus variance
}

// MARK: - Insight Text Methods

function getCurrentUseInsight(score: number): string {
  return `Your cannbis use is higher than ${score}% of young adults (18 - 25 year olds).`
}

function getHabitStrengthInsight(score: number): string {
  let insight = ""
  if (score < 20) {
    insight = "Your cannabis use looks occasional and mindful. A structured break could help you deepen this intentionality and keep your choices aligned with what matters to you."
  } else if (score < 40) {
    insight = "Cannabis is part of your routine, but mostly intentional. Taking a clear look at your habits could help you maintain balance and stay fully in control."
  } else if (score < 60) {
    insight = "Cannabis has become fairly routine for you, and habits at this level can start to feel automatic. Checking in on these patterns now could help you realign them with your bigger goals."
  } else if (score < 80) {
    insight = "Your cannabis use feels automatic and consistent. Reflecting on these habits can help you shift toward choices that match the life you want to build."
  } else {
    insight = "Your cannabis use is strongly habitual and likely automatic. Recognizing and reshaping these patterns could significantly increase your sense of control and alignment with your personal goals."
  }

  insight = insight + " Your habit score is calculated based on the days you use and your triggers."

  return insight
}

// MARK: - Strengths and Growth Areas
function getStrengths(responses: any, lastSmokedDate?: string, startDate?: string): ProgramNormativeTextSectionContentOrdered[] {
  const applicableStrengths = getApplicableStrengths(responses, lastSmokedDate, startDate)
  return applicableStrengths.sort((a, b) => a.priority - b.priority).slice(0, 2)
}

function getGrowthAreas(responses: any): ProgramNormativeTextSectionContentOrdered[] {
  const applicableGrowthAreas = getApplicableGrowthAreas(responses)
  return applicableGrowthAreas.sort((a, b) => a.priority - b.priority).slice(0, 2)
}

function getApplicableStrengths(responses: any, lastSmokedDate?: string, startDate?: string): ProgramNormativeTextSectionContentOrdered[] {
  const strengths: ProgramNormativeTextSectionContentOrdered[] = []

  // Get relevant response values
  const previousBreakResponse = responses[AssessmentQuestionID.previousBreak]?.[0] || "No break"
  const commitmentResponse = responses[AssessmentQuestionID.commitment]?.[0] || "Somewhat"
  const helpHarmResponse = responses[AssessmentQuestionID.helpHarm]?.[0] || "Equally Helping and Harming (but in different ways)"
  const daysUsingResponse = responses[AssessmentQuestionID.daysUsing]?.[0] || "About once a week or less"
  const triggersResponse = responses[AssessmentQuestionID.trigger] || []
  const breakReasonResponse = responses[AssessmentQuestionID.breakReason]?.[0] || "Improve Overall Health"
  const afterClear30Response = responses[AssessmentQuestionID.thenWhat]?.[0] || "I don't know right now"

  // Calculate days since last smoking
  let daysSinceSmoking = 0
  if (lastSmokedDate) {
    const lastSmoked = new Date(lastSmokedDate)
    const today = new Date()
    const diffTime = today.getTime() - lastSmoked.getTime()
    daysSinceSmoking = Math.floor(diffTime / (1000 * 60 * 60 * 24))
  }

  // Calculate days until start
  let daysUntilStart = 0
  if (startDate) {
    const start = new Date(startDate)
    const today = new Date()
    const diffTime = start.getTime() - today.getTime()
    daysUntilStart = Math.floor(diffTime / (1000 * 60 * 60 * 24))
  }

  // 🥇 Most Valuable & Motivating (Personal Wins)

  // Successful Break History
  if (previousBreakResponse === "Maintained") {
    strengths.push({
      title: "Successful Break History",
      description: "You've successfully completed past breaks—this experience strongly boosts your chances this time around.",
      priority: 1
    })
  }

  // Preparedness (Despite Challenges)
  if (previousBreakResponse === "Mostly maintained") {
    strengths.push({
      title: "Preparedness (Despite Challenges)",
      description: "You've persisted through challenges with past breaks—your resilience significantly predicts future success.",
      priority: 2
    })
  }

  // Commitment Clarity
  if (commitmentResponse === "Very") {
    strengths.push({
      title: "Commitment Clarity",
      description: "Your clear commitment greatly improves your chances of lasting positive change.",
      priority: 3
    })
  }

  // Recognition of Harm
  if (helpHarmResponse === "Mostly harming more than helping" || helpHarmResponse === "Somewhat harming more than helping") {
    strengths.push({
      title: "Recognition of Harm",
      description: "You're honest about cannabis being mostly harmful—this self-awareness is a powerful motivation for positive shifts.",
      priority: 4
    })
  }

  // 🥈 High Value (Insight & Motivation)

  // Trigger Insight
  if (triggersResponse.length > 0) {
    strengths.push({
      title: "Trigger Insight",
      description: "You've clearly pinpointed why you smoke—understanding these triggers makes your path forward easier.",
      priority: 5
    })
  }

  // Goal Awareness
  if (breakReasonResponse) {
    strengths.push({
      title: "Goal Awareness",
      description: "Your clear goal gives you a strong foundation for meaningful progress.",
      priority: 6
    })
  }

  // Intentionality
  if (afterClear30Response !== "I don't know right now") {
    strengths.push({
      title: "Intentionality",
      description: "You're thoughtfully considering your future cannabis use, showing readiness to act intentionally.",
      priority: 7
    })
  }

  // Desire for Mental Clarity/Productivity/Health
  if (["Gain Mental Clarity", "Increase Productivity", "Improve Overall Health", "Improve Lung Health"].includes(breakReasonResponse)) {
    strengths.push({
      title: "Desire for Mental Clarity/Productivity/Health",
      description: "Your specific desire for clearer thinking, productivity, or improved health strongly motivates change.",
      priority: 8
    })
  }

  // 🥉 Moderate Value (Encouraging Signs & Good Timing)

  // Recent Use Reduction
  if (daysSinceSmoking >= 3) {
    strengths.push({
      title: "Recent Use Reduction",
      description: "You've already cut back or paused cannabis use recently—this initial momentum positions you well.",
      priority: 9
    })
  }

  // Healthy Frequency (Low use)
  if (daysUsingResponse === "About once a week or less") {
    strengths.push({
      title: "Healthy Frequency (Low use)",
      description: "You're currently an occasional user, making your break manageable and approachable.",
      priority: 10
    })
  }

  // Immediate Action
  if (daysUntilStart === 0) {
    strengths.push({
      title: "Immediate Action",
      description: "You're proactively choosing to start right now, demonstrating strong initial energy.",
      priority: 11
    })
  }

  // Relationship Improvement Goal
  if (["Improve Current Relationships", "Enhance Social Connections"].includes(breakReasonResponse)) {
    strengths.push({
      title: "Relationship Improvement Goal",
      description: "Focusing on stronger personal connections can powerfully motivate meaningful changes.",
      priority: 12
    })
  }

  // Balanced Self-Awareness
  if (helpHarmResponse === "Equally Helping and Harming (but in different ways)") {
    strengths.push({
      title: "Balanced Self-Awareness",
      description: "Recognizing both helpful and harmful aspects of your cannabis use helps you thoughtfully navigate future decisions.",
      priority: 13
    })
  }

  // 📌 Lower Value (Still Useful, but Less Motivating)

  // Controlled Usage
  if (daysUsingResponse === "2-3 days a week" || daysUsingResponse === "4-5 days a week") {
    strengths.push({
      title: "Controlled Usage",
      description: "Your moderate frequency means small adjustments can easily lead to improvements.",
      priority: 14
    })
  }

  // Balanced Perspective (duplicate of Balanced Self-Awareness with lower priority)
  if (helpHarmResponse === "Equally Helping and Harming (but in different ways)") {
    strengths.push({
      title: "Balanced Perspective",
      description: "You see cannabis as equally helpful and harmful, showing openness to change.",
      priority: 15
    })
  }

  // Financial Motivation
  if (breakReasonResponse === "Save Money") {
    strengths.push({
      title: "Financial Motivation",
      description: "Your clear interest in saving money offers tangible benefits that might motivate your break.",
      priority: 16
    })
  }

  // 📍 Least Valuable (Basic Recognition from Assessment)

  // Financial Awareness (always present)
  strengths.push({
    title: "Financial Awareness",
    description: "(Simply answering the assessment's financial question is helpful but doesn't strongly motivate change.)",
    priority: 17
  })

  return strengths
}

function getApplicableGrowthAreas(responses: any): ProgramNormativeTextSectionContentOrdered[] {
  const growthAreas: ProgramNormativeTextSectionContentOrdered[] = []

  // Get relevant response values
  const helpHarmResponse = responses[AssessmentQuestionID.helpHarm]?.[0] || "Equally Helping and Harming (but in different ways)"
  const daysUsingResponse = responses[AssessmentQuestionID.daysUsing]?.[0] || "About once a week or less"
  const triggersResponse = responses[AssessmentQuestionID.trigger] || []
  const breakReasonResponse = responses[AssessmentQuestionID.breakReason]?.[0] || "Improve Overall Health"
  const commitmentResponse = responses[AssessmentQuestionID.commitment]?.[0] || "Somewhat"
  const afterClear30Response = responses[AssessmentQuestionID.thenWhat]?.[0] || "I don't know right now"
  const moneySpentResponse = responses[AssessmentQuestionID.moneySpent]?.[0] || "5"
  const consumptionMethodResponse = responses[AssessmentQuestionID.consumptionMethod]?.[0] || "Flower"

  // 🥇 Most Valuable (Significant Opportunity & Motivation)

  // Breaking Automatic Habits
  if (triggersResponse.includes("Out of habit") &&
    (daysUsingResponse === "4-5 days a week" || daysUsingResponse === "6-7 days a week")) {
    growthAreas.push({
      title: "Breaking Automatic Habits",
      description: "Your cannabis use is routine and automatic. Shifting toward intentional choices can help you regain a strong sense of control.",
      priority: 2
    })
  }

  // Improving Emotional Coping Strategies
  if (triggersResponse.includes("Stress relief / relax") || triggersResponse.includes("Escape from negative thoughts / emotions")) {
    growthAreas.push({
      title: "Improving Emotional Coping Strategies",
      description: "You often turn to cannabis to manage tough emotions or stress. Developing healthier coping strategies could lead to lasting emotional balance.",
      priority: 3
    })
  }

  // Increasing Sense of Personal Control
  if ((commitmentResponse === "Somewhat" || commitmentResponse === "Not") &&
    (daysUsingResponse === "4-5 days a week" || daysUsingResponse === "6-7 days a week")) {
    growthAreas.push({
      title: "Increasing Sense of Personal Control",
      description: "Strengthening your sense of control around cannabis could empower you to consistently make choices aligned with what matters to you.",
      priority: 4
    })
  }

  // 🥈 Moderately Valuable (Practical, Actionable)

  // Building Better Sleep Habits
  if (triggersResponse.includes("To fall asleep") && breakReasonResponse === "Improve Sleep Quality") {
    growthAreas.push({
      title: "Building Better Sleep Habits",
      description: "You often rely on cannabis to fall asleep. Building consistent, cannabis-free routines could significantly enhance sleep quality.",
      priority: 5
    })
  }

  // Clarifying Future Cannabis Intentions
  if (afterClear30Response === "I don't know right now" &&
    (commitmentResponse === "Somewhat" || commitmentResponse === "Not")) {
    growthAreas.push({
      title: "Clarifying Future Cannabis Intentions",
      description: "You're uncertain about your future cannabis use. Clarifying your intentions can boost motivation and focus your efforts effectively.",
      priority: 6
    })
  }

  // Enhancing Social Connections without Cannabis
  if ((triggersResponse.includes("My friends") || triggersResponse.includes("My closest friend / partner")) &&
    (breakReasonResponse === "Improve Current Relationships" || breakReasonResponse === "Enhance Social Connections")) {
    growthAreas.push({
      title: "Expanding Social Connections without Cannabis",
      description: "You frequently use cannabis socially. Experimenting with cannabis-free social interactions could strengthen your relationships.",
      priority: 7
    })
  }

  // Reducing Dependency and Withdrawal
  if (triggersResponse.includes("Reduce/stop withdrawal symptoms") && breakReasonResponse === "Reduce Dependency on Cannabis") {
    growthAreas.push({
      title: "Reducing Dependency and Withdrawal",
      description: "Addressing cannabis dependency directly could help you break the cycle of withdrawal and reliance, leading to greater stability.",
      priority: 8
    })
  }

  // Managing Boredom in Healthier Ways
  if (triggersResponse.includes("Reduce boredom")) {
    growthAreas.push({
      title: "Managing Boredom in Healthier Ways",
      description: "You often use cannabis to combat boredom. Exploring more fulfilling activities could naturally decrease reliance and enhance enjoyment.",
      priority: 9
    })
  }

  // 🥉 Valuable (Specific and Tangible)

  // Reducing Cannabis Spending
  if (parseFloat(moneySpentResponse) >= 25 && breakReasonResponse === "Save Money") {
    growthAreas.push({
      title: "Reducing Cannabis Spending",
      description: "You're spending significantly on cannabis—cutting back could provide tangible savings to invest in meaningful goals.",
      priority: 10
    })
  }

  // Diversifying Pain Management Strategies
  if (triggersResponse.includes("Relieve pain")) {
    growthAreas.push({
      title: "Diversifying Pain Management Strategies",
      description: "You frequently use cannabis for pain relief. Exploring other methods could give you multiple effective tools for managing pain.",
      priority: 11
    })
  }

  // Cultivating Natural Creativity
  if (triggersResponse.includes("Enhance creativity")) {
    growthAreas.push({
      title: "Cultivating Natural Creativity",
      description: "You rely on cannabis to boost creativity. Building confidence in your natural creative abilities could offer sustainable, independent inspiration.",
      priority: 12
    })
  }

  // Improving Physical Health (Lungs)
  if (breakReasonResponse === "Improve Lung Health" &&
    (consumptionMethodResponse === "Flower" || consumptionMethodResponse === "Pen")) {
    growthAreas.push({
      title: "Improving Physical Health (Lungs)",
      description: "Reducing cannabis smoking could meaningfully improve lung health and overall wellness.",
      priority: 13
    })
  }

  // 📌 Lower Value (Situational or Specific)

  // Reducing Cannabis Use for Enhancing Tasks
  if (triggersResponse.includes("Wanting to enhance things chores, work, or movies or music")) {
    growthAreas.push({
      title: "Reducing Cannabis Use for Enhancing Tasks",
      description: "You regularly use cannabis to enhance tasks. Learning to engage fully without cannabis might offer improved long-term satisfaction.",
      priority: 14
    })
  }

  // Minimizing Cannabis Use Simply Due to Proximity
  if (triggersResponse.includes("It is available around me generally")) {
    growthAreas.push({
      title: "Minimizing Cannabis Use Simply Due to Proximity",
      description: "Your cannabis use occurs often due to convenience or presence. Adjusting your environment could simplify healthier choices.",
      priority: 15
    })
  }

  // Addressing Cannabis Use Due to Enjoying Being High
  if (triggersResponse.includes("Just like being high")) {
    growthAreas.push({
      title: "Addressing Cannabis Use Due to Enjoying Being High",
      description: "Enjoying the experience of cannabis is understandable, but exploring other rewarding experiences might diversify your sources of pleasure.",
      priority: 16
    })
  }

  // 📍 Least Valuable (Routine Answers with Low Insight)

  // Adjusting Consumption Method (always applicable)
  growthAreas.push({
    title: "Adjusting Consumption Method",
    description: "Simply changing cannabis methods offers limited overall impact on broader goals or deeper habit change.",
    priority: 17
  })

  // Addressing Cannabis as "Closest Friend/Partner"
  if (triggersResponse.includes("My closest friend / partner")) {
    growthAreas.push({
      title: "Addressing Cannabis as Closest Friend/Partner",
      description: "Viewing cannabis socially or personally important is common, but exploring new social supports may provide moderate improvements.",
      priority: 18
    })
  }

  // Minimizing Use for Legal Obligations
  if (breakReasonResponse === "Pass Work-Required Drug Test" || breakReasonResponse === "Meet Legal Obligations") {
    growthAreas.push({
      title: "Minimizing Use for Legal Obligations",
      description: "This practical, temporary goal is clear but offers limited deeper insight or growth potential beyond immediate compliance.",
      priority: 19
    })
  }

  return growthAreas
}

/* To invoke locally:

  1. Run `supabase start` (see: https://supabase.com/docs/reference/cli/supabase-start)
  2. Make an HTTP request:

  curl -i --location --request POST 'http://127.0.0.1:54321/functions/v1/program_generate_normative_feedback' \
  --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' \
  --header 'Content-Type: application/json' \
  --data '{
    "userId": "fc20545a-ca5f-4fe5-975f-52f391974eea",
    "lastSmokedDate": "2025-03-30",
    "startDate": "2025-04-03"
  }'
*/
