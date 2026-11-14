package io.github.hurokume.xrec.data

import io.github.hurokume.xrec.R

enum class TemplateApplyMode { Add, ReplaceAll }

enum class TaskTemplateType(val titleRes: Int) {
    SecondaryStudents(R.string.help_template_secondary_students),
    Undergraduates(R.string.help_template_undergraduates),
    LawStudents(R.string.help_template_law_students),
    WorkingAdults(R.string.help_template_working_adults),
    Homemakers(R.string.help_template_homemakers);

    fun projects(text: (Int) -> String): List<TaskTemplateProject> = when (this) {
        SecondaryStudents -> secondaryStudentsTemplate(text)
        Undergraduates -> undergraduateTemplate(text)
        LawStudents -> lawStudentTemplate(text)
        WorkingAdults -> workingAdultTemplate(text)
        Homemakers -> homemakerTemplate(text)
    }
}

data class TaskTemplateProject(val name: String, val tasks: List<String>)

fun secondaryStudentsTemplate(text: (Int) -> String): List<TaskTemplateProject> {
    fun project(name: Int, vararg tasks: Int) =
        TaskTemplateProject(text(name), tasks.map(text))

    return listOf(
        project(
            R.string.template_school,
            R.string.template_school_classes,
            R.string.template_homeroom,
            R.string.template_school_events,
            R.string.template_student_council
        ),
        project(
            R.string.template_home_study,
            R.string.template_review,
            R.string.template_homework,
            R.string.template_school_exams,
            R.string.template_entrance_exams,
            R.string.template_cram_school,
            R.string.template_self_study
        ),
        project(
            R.string.template_school_activities,
            R.string.template_school_clubs,
            R.string.template_lessons,
            R.string.template_volunteering,
            R.string.template_friends_family
        ),
        project(
            R.string.template_daily_life,
            R.string.template_meals,
            R.string.template_helping_at_home,
            R.string.template_personal_care,
            R.string.template_errands
        ),
        project(
            R.string.template_rest_leisure,
            R.string.template_sleep,
            R.string.template_breaks,
            R.string.template_exercise,
            R.string.template_hobbies
        ),
        project(R.string.template_travel, R.string.template_school_commute, R.string.template_other_travel)
    )
}

fun undergraduateTemplate(text: (Int) -> String): List<TaskTemplateProject> {
    fun project(name: Int, vararg tasks: Int) =
        TaskTemplateProject(text(name), tasks.map(text))

    return listOf(
        project(
            R.string.template_academics,
            R.string.template_classes,
            R.string.template_review,
            R.string.template_assignments,
            R.string.template_exams,
            R.string.template_research,
            R.string.template_self_study
        ),
        project(
            R.string.template_job_hunting,
            R.string.template_company_research,
            R.string.template_applications,
            R.string.template_internships
        ),
        project(R.string.template_part_time_job, R.string.template_work_shift),
        project(
            R.string.template_activities,
            R.string.template_clubs,
            R.string.template_volunteering,
            R.string.template_friends_family,
            R.string.template_dates
        ),
        project(
            R.string.template_daily_life,
            R.string.template_meals,
            R.string.template_housework,
            R.string.template_personal_care,
            R.string.template_errands
        ),
        project(
            R.string.template_rest_leisure,
            R.string.template_sleep,
            R.string.template_breaks,
            R.string.template_exercise,
            R.string.template_hobbies
        ),
        project(R.string.template_travel, R.string.template_commute, R.string.template_other_travel)
    )
}

fun lawStudentTemplate(text: (Int) -> String): List<TaskTemplateProject> {
    fun project(name: Int, vararg tasks: Int) =
        TaskTemplateProject(text(name), tasks.map(text))

    return listOf(
        project(
            R.string.template_law_studies,
            R.string.template_lectures_seminars,
            R.string.template_review,
            R.string.template_case_law_reading,
            R.string.template_assignments,
            R.string.template_law_exams
        ),
        project(
            R.string.template_bar_exam,
            R.string.template_bar_multiple_choice,
            R.string.template_bar_essays,
            R.string.template_past_papers,
            R.string.template_answer_review
        ),
        project(
            R.string.template_legal_career,
            R.string.template_legal_practice,
            R.string.template_law_firm_research,
            R.string.template_applications
        ),
        project(
            R.string.template_daily_life,
            R.string.template_meals,
            R.string.template_housework,
            R.string.template_personal_care,
            R.string.template_errands
        ),
        project(R.string.template_socializing, R.string.template_friends_family, R.string.template_volunteering),
        project(
            R.string.template_rest_leisure,
            R.string.template_sleep,
            R.string.template_breaks,
            R.string.template_exercise,
            R.string.template_hobbies
        ),
        project(R.string.template_travel, R.string.template_commute, R.string.template_other_travel)
    )
}

fun workingAdultTemplate(text: (Int) -> String): List<TaskTemplateProject> {
    fun project(name: Int, vararg tasks: Int) =
        TaskTemplateProject(text(name), tasks.map(text))

    return listOf(
        project(
            R.string.template_work,
            R.string.template_focused_work,
            R.string.template_meetings,
            R.string.template_messages,
            R.string.template_documents_admin,
            R.string.template_client_work,
            R.string.template_work_planning
        ),
        project(
            R.string.template_learning_career,
            R.string.template_training,
            R.string.template_skills,
            R.string.template_career_planning
        ),
        project(
            R.string.template_family_socializing,
            R.string.template_family_support,
            R.string.template_friends_family,
            R.string.template_community
        ),
        project(
            R.string.template_daily_life,
            R.string.template_meals,
            R.string.template_housework,
            R.string.template_personal_care,
            R.string.template_errands
        ),
        project(
            R.string.template_rest_leisure,
            R.string.template_sleep,
            R.string.template_breaks,
            R.string.template_exercise,
            R.string.template_hobbies
        ),
        project(
            R.string.template_travel,
            R.string.template_work_commute,
            R.string.template_business_travel,
            R.string.template_other_travel
        )
    )
}

fun homemakerTemplate(text: (Int) -> String): List<TaskTemplateProject> {
    fun project(name: Int, vararg tasks: Int) =
        TaskTemplateProject(text(name), tasks.map(text))

    return listOf(
        project(
            R.string.template_home_chores,
            R.string.template_meal_preparation,
            R.string.template_dishes,
            R.string.template_cleaning,
            R.string.template_laundry,
            R.string.template_grocery_shopping
        ),
        project(
            R.string.template_family_care,
            R.string.template_childcare,
            R.string.template_homework_support,
            R.string.template_caregiving,
            R.string.template_family_transport
        ),
        project(
            R.string.template_household_management,
            R.string.template_household_budget,
            R.string.template_administrative_errands,
            R.string.template_family_schedule
        ),
        project(
            R.string.template_learning_socializing,
            R.string.template_self_study,
            R.string.template_friends_family,
            R.string.template_community
        ),
        project(
            R.string.template_daily_life,
            R.string.template_eating,
            R.string.template_personal_care,
            R.string.template_personal_appointments
        ),
        project(
            R.string.template_rest_leisure,
            R.string.template_sleep,
            R.string.template_breaks,
            R.string.template_exercise,
            R.string.template_hobbies
        ),
        project(R.string.template_travel, R.string.template_other_travel)
    )
}
