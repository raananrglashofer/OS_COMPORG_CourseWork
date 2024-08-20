#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <semaphore.h>
#include <unistd.h>
#include <time.h>
#include <stdint.h>

// Define your struct for (x, y) and (row, column)
typedef struct {
    double x;
    double y;
    int row;
    int column;
} Coordinate;

// Define BMP file headers
#pragma pack(push, 1)
typedef struct {
    uint16_t type;
    uint32_t size;
    uint32_t reserved1;
    uint32_t offset;
} BmpFileHeader;

typedef struct {
    uint32_t size;
    int32_t width;
    int32_t height;
    uint16_t planes;
    uint16_t bits;
    uint32_t compression;
    uint32_t imagesize;
    int32_t xresolution;
    int32_t yresolution;
    uint32_t ncolors;
    uint32_t importantcolors;
} BmpInfoHeader;
#pragma pack(pop)

// Global variables
int img_dim;  // Image dimension
int engines;  // Number of computational engines
double ul_x, ul_y, mandel_dim;  // Command-line arguments
Coordinate *coordinates;  // Array to hold (x, y) and (row, column) information
pthread_t *engine_threads;  // Array to store engine thread identifiers
pthread_t *column_threads;
sem_t *engine_semaphores;  // Array of semaphores for each engine
sem_t *finished_semaphores;
sem_t output_semaphore;  // Semaphore for controlling access to the row data array
pthread_barrier_t row_barrier;  // Barrier for synchronizing rows
pthread_barrier_t column_barrier;
pthread_mutex_t output_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t *engine_coordinates_mutex;
int *row_data;
sem_t termination_semaphore;
pthread_t bit_map_thread;
//int *bitmap_data;
FILE* bmpFile;
int count = 0;
int bitMapDone = 0;

// Function prototypes
void *engine_thread_function(void *arg);
void *column_thread_function(void *arg);
void *write_bmp_row(void *arg);
//void createBitmap(const char* filename, int width, int height, int* pixels);

int main(int argc, char *argv[]) {
    // Seed the random number generator
    srand((unsigned int)time(NULL));

    // Check command-line arguments
    if (argc != 6) {
        fprintf(stderr, "Usage: %s img_dim engines UL_X UL_Y mandel_dim\n", argv[0]);
        exit(EXIT_FAILURE);
    }

    // Parse command-line arguments
    img_dim = atoi(argv[1]);
    engines = atoi(argv[2]);
    ul_x = atof(argv[3]);
    ul_y = atof(argv[4]);
    mandel_dim = atof(argv[5]);

    // Allocate memory for the row data array
    row_data = (int *)malloc(img_dim * sizeof(int));
    //bitmap_data = (int *)malloc(img_dim * img_dim * sizeof(int));


    // Allocate memory for the coordinates array
    coordinates = (Coordinate *)malloc(engines * sizeof(Coordinate));

    // Allocate memory for the engine threads and semaphores
    engine_threads = (pthread_t *)malloc(engines * sizeof(pthread_t));
    engine_semaphores = (sem_t *)malloc(engines * sizeof(sem_t));
    finished_semaphores = (sem_t *)malloc(engines * sizeof(sem_t));

    // Initialize semaphores
    for (int i = 0; i < engines; ++i) {
        sem_init(&engine_semaphores[i], 0, 0); // why is this 0, 0 ??
        sem_init(&finished_semaphores[i], 0, 1);
    }

    // Initialize output semaphore
    sem_init(&output_semaphore, 0, 0);
   // sem_init(&termination_semaphore, 0, 1);  // Initialized to 0

    // Initialize thread barrier
    pthread_barrier_init(&row_barrier, NULL, img_dim + 1); // going to have to be img_dim + 1 for bit map purposes
    pthread_barrier_init(&column_barrier, NULL, img_dim);

    // Create engine threads
    int *engine_indices = (int *)malloc(engines * sizeof(int));

    // Initialize output mutex
    pthread_mutex_init(&output_mutex, NULL);
    engine_coordinates_mutex = (pthread_mutex_t *)malloc(engines * sizeof(pthread_mutex_t));
    for(int i = 0; i < engines; ++i){
        pthread_mutex_init(&engine_coordinates_mutex[i], NULL);
    }

    for (int i = 0; i < engines; ++i) {
        engine_indices[i] = i;
        pthread_create(&engine_threads[i], NULL, engine_thread_function, (void *)&engine_indices[i]);
    }

    // create bit map thread
    pthread_create(&bit_map_thread, NULL, write_bmp_row, (void *)&bit_map_thread);

    // Create column threads
    int *column_indices = (int *)malloc(img_dim * sizeof(int));
    column_threads = (pthread_t *)malloc(img_dim * sizeof(pthread_t));
    for (int i = 0; i < img_dim; ++i) {
        column_indices[i] = i;
        //pthread_t column_thread;
        pthread_create(&column_threads[i], NULL, column_thread_function, (void *)&column_indices[i]);
       // pthread_detach(column_thread);  // Detach the column threads
    }


    // // wait for semaphores to finish
    // for(int i = 0; i < engines; ++i){
    //     sem_wait(&engine_semaphores[i]);
    //     sem_wait(&finished_semaphores[i]);
    // }

    // sem_post(&termination_semaphore);
    // // // Signal the threads to terminate
    // // for (int i = 0; i < engines; ++i) {
    // //     sem_post(&termination_semaphore);
    // // }

    for (int i = 0; i < img_dim; ++i) {
       // printf("Trying to Join Column: %d\n", i);
        pthread_join(column_threads[i], NULL);
       // free(column_threads[i]);
    }
   // printf("starting to join threads \n");
    //Wait for engine threads to finish  
    // for (int i = 0; i < engines; ++i) {
    //    // printf("Trying to Join Engine: %d\n", i);
    //     pthread_join(engine_threads[i], NULL);
    // }

    // for(int i = 0; i < img_dim; ++i){

    //     pthread_join(column_threads[i], NULL);
    // }




    // need to clean these two things up 
    // Cleanup resources - need to add a lot of things to this list (arrays)
    //printf("Starting to clear memory \n");
    free(coordinates);
    free(row_data);
    free(engine_threads);
    free(column_threads);
    free(engine_semaphores);
    free(finished_semaphores);
    free(engine_coordinates_mutex);
    for(int j = 0; j < engines; ++j){
        sem_destroy(&engine_semaphores[j]);
        sem_destroy(&finished_semaphores[j]);
        pthread_mutex_destroy(&engine_coordinates_mutex[j]);
    }
    sem_destroy(&output_semaphore);
    sem_destroy(&termination_semaphore);
    pthread_mutex_destroy(&output_mutex);
    return 0;
}

void *engine_thread_function(void *arg) {
    //printf("Start of Engine Thread Function \n");
    int my_engine = *((int *)arg); // The index of the current engine.
    count = 0;
    while (1) {
        if(bitMapDone){
            break;
        }
        // Wait for the signal to start processing this engine's assigned pixel.
        sem_wait(&engine_semaphores[my_engine]);
        //printf("After sem_wait for engine_semaphore: %d\n", my_engine);
        // Retrieve coordinates for this engine's work.
        pthread_mutex_lock(&engine_coordinates_mutex[my_engine]);
        double x0 = coordinates[my_engine].x;
        double y0 = coordinates[my_engine].y;
        int column = coordinates[my_engine].column;
        ++count;
        pthread_mutex_unlock(&engine_coordinates_mutex[my_engine]);

        // Initialize the iteration variables for the Mandelbrot computation.
        double x = 0.0, y = 0.0;
        int iteration = 0;
        int max_iteration = 255;
        while (x*x + y*y < 4 && iteration < max_iteration) {
            double xtemp = x*x - y*y + x0;
            y = 2*x*y + y0;
            x = xtemp;
            iteration++;
        }

        // Map the iteration count to a color/luminosity value.
        int value = 255 - iteration;

        // Wait for the semaphore before writing to the row data array.
        //sem_wait(&output_semaphore);
        pthread_mutex_lock(&output_mutex);
        //printf("Engine Number: %d\n", my_engine);
       // printf("coordinates[my_engine].column: %d\n", coordinates[my_engine].column);
        row_data[column] = value;
        pthread_mutex_unlock(&output_mutex);
        //sem_post(&output_semaphore);


        sem_post(&finished_semaphores[my_engine]);

       // printf("After sem_post for finished_semaphore \n");
    }
   // printf("engines should print 9 times \n");
   //printf("engine thread is nifter \n");
    pthread_exit(NULL);
}




void *column_thread_function(void *arg) {
    //printf("Start of Column thread function \n");
    int my_column = *((int *)arg);
    //img_dim
    for(int row = 0; row < img_dim; ++row) {
       // printf("Column Waiting: %d\n", my_column);
        pthread_barrier_wait(&column_barrier);
        //printf("Column Number in Column Function: %d\n", my_column);
        // Step A: Randomly select a computational engine
        int random_engine = rand() % engines;
        sem_wait(&finished_semaphores[random_engine]);
        //printf("After sem_wait for finished_semaphore %d\n", random_engine);
        // Step B: Calculate (x, y) coordinates in the complex plane
        double x = ul_x + (double) my_column * mandel_dim / img_dim;
        double y = ul_y + mandel_dim - (double)row * mandel_dim / img_dim;
        // Step C: Write to the coordinates array using a mutex
        pthread_mutex_lock(&engine_coordinates_mutex[random_engine]);
        coordinates[random_engine].x = x;
        coordinates[random_engine].y = y;
        coordinates[random_engine].row = row;
        coordinates[random_engine].column = my_column;
        pthread_mutex_unlock(&engine_coordinates_mutex[random_engine]);
        // Step D: Signal the corresponding engine thread to start processing
        sem_post(&engine_semaphores[random_engine]);
        //printf("After sem_wait for engine \n");
        pthread_barrier_wait(&row_barrier);
        sem_wait(&output_semaphore);
    }
    //printf("column threads finsihed \n");
    pthread_exit(NULL);
}

void *write_bmp_row(void *arg){
    int height = img_dim;
    int width = img_dim;
    BmpFileHeader fileHeader = { 0x4D42, sizeof(BmpFileHeader) + sizeof(BmpInfoHeader) + width * height * 3, 
                                0, sizeof(BmpFileHeader) + sizeof(BmpInfoHeader) };
    BmpInfoHeader infoHeader = { sizeof(BmpInfoHeader), width, height, 1, 24, 0, width * height * 3, 0, 0, 0, 0 };
    bmpFile = fopen("mandelbrot.bmp", "wb");
        
    // Write headers
    fwrite(&fileHeader, sizeof(BmpFileHeader), 1, bmpFile);
    fwrite(&infoHeader, sizeof(BmpInfoHeader), 1, bmpFile);
    for(int n = 0; n < img_dim; ++n){ // should be while(1)
        //printf("here \n");
        //for(int i = 0; i < img_dim; ++i){
        pthread_barrier_wait(&row_barrier);
       // }
        // for(int i = 0; i < img_dim; ++i){
        //     sem_wait(&output_semaphore);
        // }
        pthread_mutex_lock(&output_mutex);
        for(int i = 0; i < img_dim; ++i){
            sem_post(&output_semaphore);
        }
        for(int j = 0; j < img_dim; ++j){
           // printf("%d ", row_data[j] );
            uint8_t current = row_data[j];
            fwrite(&current, 1, 1, bmpFile);
            fwrite(&current, 1, 1, bmpFile);
            fwrite(&current, 1, 1, bmpFile);
        }
        pthread_mutex_unlock(&output_mutex);
    }
    fclose(bmpFile);
    // for(int n = 0; n < engines; ++n){
    //     pthread_exit(&engine_threads[n]); // shouldn't be null
    // }
    //printf("bit map finished \n");
    bitMapDone = 1;
    return(NULL);
}
