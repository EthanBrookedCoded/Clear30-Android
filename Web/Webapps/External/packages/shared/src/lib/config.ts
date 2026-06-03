export interface AppConfig {
  supabaseUrl: string;
  supabaseAnonKey: string;
  isQA: boolean;
  isDevelopment: boolean;
}

export const getConfig = (): AppConfig => {
  const isQA = false;
  const isDevelopment = process.env.NODE_ENV === 'development';

  return {
    supabaseUrl: isQA
      ? 'http://127.0.0.1:54321'
      : 'https://quluipmdicjsolnsopkg.supabase.co',
    supabaseAnonKey: isQA
      ? 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0' // Replace with your QA Supabase anon key,
      : 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MjQ2OTU4MzcsImV4cCI6MjA0MDI3MTgzN30.aXy2DJAU7pmR-yiniP57d0moGd-REDMlnWi8D4DkQgU', // Replace with your production Supabase anon key
    isQA,
    isDevelopment
  };
};
